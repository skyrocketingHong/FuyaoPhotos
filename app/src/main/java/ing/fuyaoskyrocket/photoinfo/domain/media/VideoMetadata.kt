package ing.fuyaoskyrocket.photoinfo.domain.media

import ing.fuyaoskyrocket.photoinfo.domain.model.ExportOptions
import java.io.File
import java.io.RandomAccessFile

/** Removes container metadata without remuxing, transcoding, or changing sample offsets/timing. */
object VideoMetadata {
    private data class Box(val start: Long, val size: Long, val header: Long, val type: String) {
        val payload get() = start + header
        val end get() = start + size
    }
    private data class Patch(val start: Long, val length: Long, val literal: ByteArray? = null)
    private val containers = setOf("moov", "trak", "mdia", "minf", "stbl", "edts", "dinf")
    private val structural = setOf("ftyp", "mdat", "wide", "iods", "vmhd", "smhd", "dref", "elst",
        "stsd", "stts", "ctts", "stss", "stsz", "stz2", "stsc", "stco", "co64", "sdtp", "sgpd", "sbgp", "cslg")

    fun copy(source: File, offset: Long, length: Long, target: File, options: ExportOptions) {
        target.outputStream().use { JpegContainer.copyRange(source, offset, length, it) }
        MotionPhoto.validateVideo(target, 0, length)
        if (options.keepExif && options.keepLocation && options.keepCaptureTime) return
        val patches = RandomAccessFile(target, "r").use { input -> plan(input, length, options) }
        RandomAccessFile(target, "rw").use { output ->
            val zeros = ByteArray(8192)
            patches.forEach { patch ->
                output.seek(patch.start)
                if (patch.literal != null) output.write(patch.literal)
                else {
                    var remaining = patch.length
                    while (remaining > 0) {
                        val size = minOf(remaining, zeros.size.toLong()).toInt()
                        output.write(zeros, 0, size); remaining -= size
                    }
                }
            }
        }
        // Every byte outside the approved metadata ranges must remain identical, including mdat.
        var start = 0L
        for (patch in patches) {
            check(MotionPhoto.digest(source, offset + start, patch.start - start)
                .contentEquals(MotionPhoto.digest(target, start, patch.start - start)))
            start = patch.start + patch.length
        }
        check(MotionPhoto.digest(source, offset + start, length - start)
            .contentEquals(MotionPhoto.digest(target, start, length - start)))
        MotionPhoto.validateVideo(target, 0, length)
    }

    private fun plan(input: RandomAccessFile, length: Long, options: ExportOptions): List<Patch> {
        val patches = mutableListOf<Patch>()
        var count = 0
        fun bytes(start: Long, size: Int): ByteArray = ByteArray(size).also { input.seek(start); input.readFully(it) }
        fun uint(start: Long): Long { input.seek(start); return input.readInt().toLong() and 0xffffffffL }
        fun boxes(start: Long, end: Long): List<Box> {
            val result = mutableListOf<Box>(); var at = start
            while (at < end) {
                // ASVS 2.2.1/5.2.1: bound box counts, sizes and nesting before traversing metadata.
                require(++count <= 100_000 && end - at >= 8) { "Unsupported video metadata" }
                var size = uint(at); var header = 8L
                if (size == 1L) { require(end - at >= 16); input.seek(at + 8); size = input.readLong(); header = 16 }
                if (size == 0L) size = end - at
                require(size >= header && size <= end - at) { "Invalid video box" }
                result += Box(at, size, header, bytes(at + 4, 4).toString(Charsets.ISO_8859_1)); at += size
            }
            return result
        }
        fun zero(start: Long, size: Long) { if (size > 0) patches += Patch(start, size) }
        fun remove(box: Box) {
            patches += Patch(box.start + 4, 4, "free".toByteArray(Charsets.US_ASCII))
            zero(box.payload, box.size - box.header)
        }
        fun keepKey(key: String): Boolean {
            val normalized = key.lowercase(java.util.Locale.ROOT)
            return when {
                listOf("location", "gps", "latitude", "longitude", "altitude").any { it in normalized } || key in setOf("©xyz", "loci") -> options.keepLocation
                listOf("date", "time").any { it in normalized } || key == "©day" -> options.keepCaptureTime
                listOf("manufacturer", "model", "marketname", "version", "make", "software", "file.type", "author", "copyright", "title").any { it in normalized } || key in setOf("©mak", "©mod", "©swr", "©nam", "©ART", "cprt") -> options.keepExif
                else -> false // Unknown descriptive metadata may carry location/time; never silently retain it.
            }
        }
        fun cleanHandler(box: Box, track: Boolean) {
            require(box.size - box.header >= 24)
            val handler = bytes(box.payload + 8, 4).toString(Charsets.US_ASCII)
            // Timed metadata/location tracks cannot be stripped without changing the track graph.
            if (track) require(handler in setOf("vide", "soun")) { "Unsupported video metadata track" }
            zero(box.payload + 24, box.end - box.payload - 24)
        }
        fun cleanMeta(box: Box) {
            require(box.size - box.header >= 8)
            // QuickTime meta starts with a child box; ISO meta starts with version/flags.
            val full = uint(box.payload) == 0L
            val children = boxes(box.payload + if (full) 4 else 0, box.end)
            val keys = mutableMapOf<Long, String>()
            children.singleOrNull { it.type == "keys" }?.let { keyBox ->
                require(keyBox.size - keyBox.header >= 8 && uint(keyBox.payload) == 0L)
                val total = uint(keyBox.payload + 4); require(total <= 4096)
                var at = keyBox.payload + 8
                for (index in 1..total) {
                    require(keyBox.end - at >= 8)
                    val size = uint(at); require(size in 8..4096 && size <= keyBox.end - at)
                    keys[index] = if (bytes(at + 4, 4).toString(Charsets.US_ASCII) == "mdta")
                        bytes(at + 8, (size - 8).toInt()).toString(Charsets.UTF_8) else ""
                    at += size
                }
                require(at == keyBox.end)
            }
            children.forEach { child ->
                when (child.type) {
                    "keys" -> Unit
                    "hdlr" -> cleanHandler(child, false)
                    "ilst" -> boxes(child.payload, child.end).forEach { item ->
                        val key = if (keys.isEmpty()) item.type else keys[uint(item.start + 4)].orEmpty()
                        if (!keepKey(key)) remove(item)
                    }
                    else -> remove(child)
                }
            }
        }
        fun walk(start: Long, end: Long, depth: Int, parent: String) {
            require(depth <= 12) { "Video nesting too deep" }
            boxes(start, end).forEach { box ->
                when {
                    box.type == "meta" -> cleanMeta(box)
                    box.type == "udta" -> boxes(box.payload, box.end).forEach { child ->
                        if (child.type == "meta") cleanMeta(child) else if (!keepKey(child.type)) remove(child)
                    }
                    box.type in setOf("uuid", "XMP_", "xml ") -> remove(box)
                    box.type == "free" || box.type == "skip" -> zero(box.payload, box.size - box.header)
                    box.type in containers -> walk(box.payload, box.end, depth + 1, box.type)
                    box.type in setOf("mvhd", "tkhd", "mdhd") -> {
                        val version = bytes(box.payload, 1)[0].toInt() and 255
                        require(version in 0..1)
                        val fields = if (version == 1) 16L else 8L
                        require(box.size - box.header >= 4 + fields)
                        if (!options.keepCaptureTime) zero(box.payload + 4, fields)
                    }
                    box.type == "hdlr" -> cleanHandler(box, parent == "mdia")
                    box.type in structural -> Unit
                    else -> error("Unsupported video box for metadata removal")
                }
            }
        }
        walk(0, length, 0, "")
        val sorted = patches.sortedBy { it.start }
        var end = 0L
        sorted.forEach { require(it.start >= end && it.length <= length - it.start); end = it.start + it.length }
        return sorted
    }
}
