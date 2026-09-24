package ing.fuyaoskyrocket.photoinfo.domain.media

import ing.fuyaoskyrocket.photoinfo.domain.media.IsoBmff.box
import ing.fuyaoskyrocket.photoinfo.domain.media.IsoBmff.data
import ing.fuyaoskyrocket.photoinfo.domain.media.IsoBmff.full
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.roundToLong

/** Adds QuickTime pairing/cover metadata; existing media chunks stay at their original offsets. */
internal object AppleLivePhotoMovie {
    private const val IDENTIFIER = "com.apple.quicktime.content.identifier"
    private const val STILL_TIME = "com.apple.quicktime.still-image-time"
    private val one = "\u0000\u0000\u0000\u0001"

    fun write(source: File, output: File, identifier: String, timestampUs: Long) {
        require(source.length() in 16..IsoBmff.MAX_BYTES.toLong())
        require(UUID.fromString(identifier).toString().equals(identifier, true))
        val bytes = source.readBytes()
        val top = IsoBmff.boxes(bytes)
        require(top.all { it.type in setOf("ftyp", "mdat", "moov", "free", "skip", "wide") })
        val ftyp = top.single { it.type == "ftyp" }
        val moov = top.single { it.type == "moov" }
        val children = IsoBmff.boxes(bytes, moov.payload, moov.end)
        val mvhd = children.single { it.type == "mvhd" }
        val version = bytes[mvhd.payload].toInt()
        require(version in 0..1)
        val timeAt = mvhd.payload + if (version == 0) 12 else 20
        require(timeAt + (if (version == 0) 8 else 12) <= mvhd.end)
        val timescale = IsoBmff.uint(bytes, timeAt)
        val duration = if (version == 0) IsoBmff.uint(bytes, timeAt + 4) else ByteBuffer.wrap(bytes, timeAt + 4, 8).long
        require(timescale in 1..1_000_000_000 && duration > 0)
        val seconds = if (timestampUs < 0) duration.toDouble() / timescale / 2 else timestampUs / 1_000_000.0
        require(seconds >= 0 && seconds < duration.toDouble() / timescale) { "Cover time lies outside the movie" }
        val tracks = children.filter { it.type == "trak" }
        require(tracks.size in 1..16)
        val trackIDs = tracks.map { track ->
            val header = IsoBmff.boxes(bytes, track.payload, track.end).single { it.type == "tkhd" }
            val offset = header.payload + if (bytes[header.payload].toInt() == 0) 12 else 20
            IsoBmff.uint(bytes, offset).also { require(it in 1..Int.MAX_VALUE - 2) }.toInt()
        }
        val videoIndex = tracks.indexOfFirst { track ->
            val mdia = IsoBmff.boxes(bytes, track.payload, track.end).single { it.type == "mdia" }
            val handler = IsoBmff.boxes(bytes, mdia.payload, mdia.end).single { it.type == "hdlr" }
            String(bytes, handler.payload + 8, 4, Charsets.US_ASCII) == "vide"
        }
        require(videoIndex >= 0)
        val nextID = trackIDs.max() + 1
        val marker = box(one, byteArrayOf(0))
        val track = markerTrack(nextID, trackIDs[videoIndex], timescale, seconds, bytes.size + 8, marker.size)
        val meta = movieMetadata(identifier, children.singleOrNull { it.type == "meta" }?.raw(bytes))
        val movie = box("moov", data {
            children.filter { it.type != "meta" }.forEach { child ->
                val raw = child.raw(bytes)
                if (child.type == "mvhd") ByteBuffer.wrap(raw).putInt(raw.size - 4, nextID + 1)
                write(raw)
            }
            write(track); write(meta)
        })
        require(ftyp.end - ftyp.payload >= 8 && (ftyp.end - ftyp.payload) % 4 == 0)
        // Retiring moov in place avoids touching stco/co64, edit lists, rotation or compressed samples.
        "free".toByteArray().copyInto(bytes, moov.start + 4)
        bytes.fill(0, moov.payload, moov.end)
        "qt  ".toByteArray().copyInto(bytes, ftyp.payload)
        bytes.fill(0, ftyp.payload + 4, ftyp.payload + 8)
        for (at in ftyp.payload + 8 until ftyp.end step 4) "qt  ".toByteArray().copyInto(bytes, at)
        // A size-zero mdat would otherwise swallow the appended metadata track.
        top.filter { IsoBmff.uint(bytes, it.start) == 0L }.forEach {
            ByteBuffer.wrap(bytes).putInt(it.start, it.end - it.start)
        }
        output.outputStream().buffered().use { it.write(bytes); it.write(box("mdat", marker)); it.write(movie) }
        top.filter { it.type == "mdat" }.forEach { part ->
            check(MotionPhoto.digest(source, part.payload.toLong(), (part.end - part.payload).toLong())
                .contentEquals(MotionPhoto.digest(output, part.payload.toLong(), (part.end - part.payload).toLong())))
        }
        MotionPhoto.validateVideo(output, 0, output.length())
    }

    private fun markerTrack(id: Int, videoID: Int, movieScale: Long, seconds: Double, offset: Int, sampleBytes: Int): ByteArray {
        val start = (seconds * movieScale).roundToLong()
        val markerTicks = maxOf(1, movieScale / 600)
        require(start + markerTicks <= 0xffffffffL)
        val tkhd = full("tkhd", flags = 0xf, payload = data {
            writeInt(0); writeInt(0); writeInt(id); writeInt(0); writeInt((start + markerTicks).toInt())
            write(ByteArray(16))
            listOf(0x10000, 0, 0, 0, 0x10000, 0, 0, 0, 0x40000000).forEach(::writeInt)
            writeLong(0)
        })
        val edts = box("edts", full("elst", payload = data {
            writeInt(if (start > 0) 2 else 1)
            if (start > 0) { writeInt(start.toInt()); writeInt(-1); writeInt(0x10000) }
            writeInt(markerTicks.toInt()); writeInt(0); writeInt(0x10000)
        }))
        val mdhd = full("mdhd", payload = data {
            writeLong(0); writeInt(600); writeInt(1); writeShort(0x55c4); writeShort(0)
        })
        val handler = full("hdlr", payload = data {
            writeBytes("mhlrmetaappl"); writeInt(1); writeInt(0); writeByte(19); writeBytes("Core Media Metadata")
        })
        val gmhd = box("gmhd", full("gmin", payload = data {
            writeShort(0x40); repeat(3) { writeShort(0x8000) }; writeInt(0)
        }))
        val dinf = box("dinf", full("dref", payload = data { writeInt(1); write(full("url ", flags = 1, payload = byteArrayOf())) }))
        val key = box(one, box("keyd", "mdta$STILL_TIME".toByteArray()), box("dtyp", data { writeInt(0); writeInt(65) }))
        val mebx = box("mebx", ByteArray(6), byteArrayOf(0, 1), box("keys", key))
        val stbl = box("stbl", full("stsd", payload = data { writeInt(1); write(mebx) }),
            full("stts", payload = data { writeInt(1); writeInt(1); writeInt(1) }),
            full("stsc", payload = data { repeat(4) { writeInt(1) } }),
            full("stsz", payload = data { writeInt(sampleBytes); writeInt(1) }),
            full("stco", payload = data { writeInt(1); writeInt(offset) }))
        return box("trak", tkhd, box("tref", box("cdsc", data { writeInt(videoID) })), edts,
            box("mdia", mdhd, handler, box("minf", gmhd, dinf, stbl)))
    }

    private fun movieMetadata(identifier: String, existing: ByteArray?): ByteArray {
        val previousKeys = mutableListOf<ByteArray>()
        val previousValues = mutableListOf<ByteArray>()
        if (existing != null) {
            val parent = IsoBmff.boxes(existing).single()
            val fullOffset = if (IsoBmff.uint(existing, parent.payload) == 0L) 4 else 0
            val children = IsoBmff.boxes(existing, parent.payload + fullOffset, parent.end)
            val keys = children.singleOrNull { it.type == "keys" }
            require(keys != null) { "Unsupported movie metadata" }
            val entries = IsoBmff.boxes(existing, keys.payload + 8, keys.end)
            require(entries.size.toLong() == IsoBmff.uint(existing, keys.payload + 4))
            require(entries.none { String(existing, it.payload, it.end - it.payload, Charsets.UTF_8) == IDENTIFIER })
            previousKeys += entries.map { it.raw(existing) }
            children.singleOrNull { it.type == "ilst" }?.let { list ->
                previousValues += IsoBmff.boxes(existing, list.payload, list.end).map { it.raw(existing) }
            }
        }
        val index = previousKeys.size + 1
        val handler = full("hdlr", payload = data { writeInt(0); writeBytes("mdta"); write(ByteArray(12)); writeByte(0) })
        val keys = full("keys", payload = data {
            writeInt(index); previousKeys.forEach(::write); write(box("mdta", IDENTIFIER.toByteArray()))
        })
        val name = ByteBuffer.allocate(4).putInt(index).array().toString(Charsets.ISO_8859_1)
        val value = box(name, box("data", data { writeInt(1); writeInt(0); writeBytes(identifier) }))
        return box("meta", handler, keys, box("ilst", *(previousValues + listOf(value)).toTypedArray()))
    }
}
