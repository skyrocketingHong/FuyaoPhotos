package ing.fuyaoskyrocket.photoinfo.domain.media

import ing.fuyaoskyrocket.photoinfo.R
import java.io.File
import java.util.Locale
import kotlin.math.pow

/**
 * Best-effort technical metadata report for one photo, mirroring the Apple report:
 * rows carry a string resource label plus either a verbatim technical value or a
 * resource-backed standard name, and missing facts simply drop out.
 */
data class MediaMetadataReport(val sections: List<Section>) {
    data class Section(val title: Int, val rows: List<Row>)
    data class Row(val label: Int, val text: String? = null, val value: Int? = null)
}

object MediaMetadataReportReader {
    fun read(mime: String, file: File, envelope: MediaEnvelope, graph: HeifGraph.Report?,
             xmp: String?, formatBytes: (Long) -> String = Long::toString): MediaMetadataReport {
        val heic = mime in setOf("image/heic", "image/heif", "image/avif")
        val jpeg = mime == "image/jpeg"
        val scan = if (jpeg) runCatching { scanJpeg(file) }.getOrNull() else null
        fun bytes(value: Long) = formatBytes(value)

        val containerRows = buildList {
            if (mime.isNotEmpty()) add(MediaMetadataReport.Row(R.string.media_report_format, mime))
            graph?.brands?.takeIf { it.isNotEmpty() }?.let {
                add(MediaMetadataReport.Row(R.string.media_report_brands, it.joinToString(" / ")))
            }
            graph?.primaryBitDepth?.let { add(MediaMetadataReport.Row(R.string.media_report_bit_depth, "$it bit")) }
            scan?.takeIf { it.trailingBytes > 0 }?.let {
                add(MediaMetadataReport.Row(R.string.media_report_trailing_data, bytes(it.trailingBytes)))
            }
        }

        val colorRows = buildList {
            if (graph?.colrPrimaries != null && graph.colrTransfer != null) {
                add(MediaMetadataReport.Row(R.string.media_report_color_gamut,
                    "${transferName(graph.colrTransfer)} / ${primariesName(graph.colrPrimaries)}"))
            }
        }

        val hdrRows = buildList {
            val facts = graph?.toneMapPayload?.let { runCatching { IsoGainMapMetadata.parse(it) }.getOrNull() }
            if (facts != null) {
                add(MediaMetadataReport.Row(R.string.media_report_hdr_standard, value = R.string.media_report_value_isogainmap))
                add(MediaMetadataReport.Row(R.string.media_report_hdr_parameters, facts.description()))
            } else if (graph?.auxiliaryURNs?.any { it.contains("hdrgainmap") } == true) {
                add(MediaMetadataReport.Row(R.string.media_report_hdr_standard, value = R.string.media_report_value_applegainmap))
            } else if (heic) {
                add(MediaMetadataReport.Row(R.string.media_report_hdr_standard, value = R.string.media_report_value_none))
            } else if (jpeg) {
                val hasXmpGainMap = xmp?.contains("hdrgm:") == true || xmp?.contains("hdr-gain-map") == true
                if (hasXmpGainMap && scan?.mpf == true) {
                    add(MediaMetadataReport.Row(R.string.media_report_hdr_standard, value = R.string.media_report_value_ultrahdr))
                } else if (scan?.isoGainMap == true || hasXmpGainMap) {
                    add(MediaMetadataReport.Row(R.string.media_report_hdr_standard, value = R.string.media_report_value_isogainmap))
                }
                xmpParameters(xmp)?.let { add(MediaMetadataReport.Row(R.string.media_report_hdr_parameters, it)) }
            }
        }

        val motionRows = buildList {
            if (!envelope.jpeg && envelope.motion != null) {
                add(MediaMetadataReport.Row(R.string.media_report_motion_standard, value = R.string.media_report_value_heicmotion))
                graph?.motionPayload?.let {
                    add(MediaMetadataReport.Row(R.string.media_report_trailing_video, bytes(it.length)))
                }
            } else if (envelope.jpeg) {
                val motion = xmp?.contains("http://ns.google.com/photos/1.0/camera/") == true &&
                    (xmp.contains("MotionPhoto") || xmp.contains("MicroVideo"))
                val trailing = scan?.trailingBytes ?: 0L
                if (motion && trailing > 0) {
                    add(MediaMetadataReport.Row(R.string.media_report_motion_standard, value = R.string.media_report_value_googlemotion))
                    add(MediaMetadataReport.Row(R.string.media_report_trailing_video, bytes(trailing)))
                }
            }
        }

        val styleRows = if (!heic) emptyList() else listOf(
            MediaMetadataReport.Row(R.string.media_report_styles_standard,
                value = if (graph?.hasStyleMetadata == true) R.string.media_report_value_styles2023
                else R.string.media_report_value_none),
            MediaMetadataReport.Row(R.string.media_report_styles_texture,
                value = if (graph?.hasTextureStyles == true) R.string.media_report_value_styles2026
                else R.string.media_report_value_none),
        )

        val vendorRows = buildList {
            if (envelope.portraitTail != null) {
                val raw = xmpNumber(xmp, "rawlength")
                val depth = xmpNumber(xmp, "depthlength")
                var text = "rawlength ${raw ?: "-"}, depthlength ${depth ?: "-"}"
                if (envelope.portraitDepthDegrees >= 0) text += ", orientation ${envelope.portraitDepthDegrees}°"
                add(MediaMetadataReport.Row(R.string.media_report_xiaomi_depth, text))
            }
            val tags = graph?.exifPayload?.let { runCatching { makerNoteTags(it) }.getOrNull() }.orEmpty()
            if (tags.isNotEmpty()) {
                add(MediaMetadataReport.Row(R.string.media_report_maker_note, tags.joinToString(", ")))
            }
            val stack = graph?.auxiliaryURNs?.count { urn ->
                urn.contains("auxid:2") || urn.contains("portraiteffectsmatte") ||
                    urn.contains("semantic") || urn.contains("hdrgainmap")
            } ?: 0
            if (stack > 0) add(MediaMetadataReport.Row(R.string.media_report_aux_stack, stack.toString()))
        }

        val groups = listOf(
            R.string.media_report_container to containerRows,
            R.string.media_report_color to colorRows,
            R.string.media_report_hdr to hdrRows,
            R.string.media_report_motion to motionRows,
            R.string.media_report_styles to styleRows,
            R.string.media_report_vendor to vendorRows,
        ).filter { it.second.isNotEmpty() }
        return MediaMetadataReport(groups.map { MediaMetadataReport.Section(it.first, it.second) })
    }

    private fun IsoGainMapMetadata.description(): String = String.format(Locale.US,
        "headroom %.2f..%.2f, gain %.2f..%.2f",
        2.0.pow(capacityMinLog2.toDouble()), 2.0.pow(capacityMaxLog2.toDouble()),
        2.0.pow(gainMinLog2.first().toDouble()), 2.0.pow(gainMaxLog2.first().toDouble()))

    private fun xmpParameters(xmp: String?): String? {
        if (xmp == null) return null
        val min = xmpDouble(xmp, "HDRCapacityMin")
        val max = xmpDouble(xmp, "HDRCapacityMax")
        val gamma = xmpDouble(xmp, "Gamma")
        val parts = mutableListOf<String>()
        if (min != null && max != null) parts.add(String.format(Locale.US, "headroom %.2f..%.2f", min, max))
        if (gamma != null) parts.add(String.format(Locale.US, "gamma %.2f", gamma))
        return parts.joinToString(", ").ifEmpty { null }
    }

    private fun primariesName(code: Int?) = when (code) {
        1 -> "BT.709"
        9 -> "BT.2020"
        12 -> "DCI-P3"
        else -> "${code ?: "-"}"
    }

    private fun transferName(code: Int?) = when (code) {
        1, 13 -> "sRGB EOTF"
        16 -> "PQ"
        18 -> "HLG"
        else -> "${code ?: "-"}"
    }

    private fun xmpNumber(xmp: String?, attribute: String): Long? {
        if (xmp == null) return null
        val start = xmp.indexOf("$attribute=\"").takeIf { it >= 0 } ?: return null
        val from = start + attribute.length + 2
        val end = xmp.indexOf('"', from).takeIf { it >= 0 } ?: return null
        return xmp.substring(from, end).toLongOrNull()
    }

    private fun xmpDouble(xmp: String?, attribute: String): Double? {
        if (xmp == null) return null
        val start = xmp.indexOf("$attribute=\"").takeIf { it >= 0 } ?: return null
        val from = start + attribute.length + 2
        val end = xmp.indexOf('"', from).takeIf { it >= 0 } ?: return null
        return xmp.substring(from, end).toDoubleOrNull()
    }

    private data class JpegScan(val mpf: Boolean, val isoGainMap: Boolean, val trailingBytes: Long)

    /** In-memory JPEG marker walk for the MPF and ISO gain-map markers plus the primary EOI. */
    private fun scanJpeg(file: File): JpegScan {
        val capped = minOf(file.length(), 32L * 1024 * 1024).toInt()
        val bytes = file.inputStream().use { input ->
            val buffer = ByteArray(capped)
            var at = 0
            while (at < capped) {
                val read = input.read(buffer, at, capped - at)
                if (read < 0) break
                at += read
            }
            if (input.read() >= 0) throw IllegalArgumentException("JPEG exceeds the report size cap")
            buffer.copyOf(at)
        }
        if (bytes.size < 4 || bytes[0] != 0xff.toByte() || bytes[1] != 0xd8.toByte()) {
            throw IllegalArgumentException("Missing SOI")
        }
        val mpfPrefix = "MPF\u0000".toByteArray(Charsets.US_ASCII)
        val isoPrefix = "urn:iso:std:iso:ts:21496".toByteArray(Charsets.US_ASCII)
        var mpf = false
        var iso = false
        var cursor = 2
        while (cursor + 4 <= bytes.size && cursor <= 4 * 1024 * 1024) {
            if (bytes[cursor] != 0xff.toByte()) break
            var marker = bytes[cursor + 1].toInt() and 255
            var advanced = cursor + 2
            while (marker == 0xff) { marker = bytes[advanced].toInt() and 255; advanced += 1 }
            if (marker == 0xda || marker == 0xd9) break
            if (marker in 0xd0..0xd7 || marker == 0x01) { cursor = advanced; continue }
            if (advanced + 2 > bytes.size) break
            val length = ((bytes[advanced].toInt() and 255) shl 8) or (bytes[advanced + 1].toInt() and 255)
            if (length < 2) break
            val payloadStart = advanced + 2
            val payloadEnd = advanced + length
            if (payloadEnd > bytes.size) break
            if (marker == 0xe2) {
                if (payloadEnd - payloadStart >= mpfPrefix.size &&
                    bytes.copyOfRange(payloadStart, payloadStart + mpfPrefix.size).contentEquals(mpfPrefix)) mpf = true
                if (payloadEnd - payloadStart >= isoPrefix.size &&
                    bytes.copyOfRange(payloadStart, payloadStart + isoPrefix.size).contentEquals(isoPrefix)) iso = true
            }
            cursor = payloadEnd
        }
        val primaryEnd = jpegEnd(bytes)
        return JpegScan(mpf, iso, (bytes.size - primaryEnd).coerceAtLeast(0).toLong())
    }

    private fun jpegEnd(bytes: ByteArray): Int {
        var at = 2
        var scanning = false
        var markers = 0
        while (at < bytes.size) {
            markers += 1
            if (markers > 100_000) throw IllegalArgumentException("JPEG marker walk overrun")
            if (scanning) {
                while (at < bytes.size && bytes[at] != 0xff.toByte()) at += 1
                if (at >= bytes.size) break
            } else if (bytes[at] != 0xff.toByte()) {
                throw IllegalArgumentException("Malformed JPEG marker")
            }
            while (at < bytes.size && bytes[at] == 0xff.toByte()) at += 1
            if (at >= bytes.size) break
            val marker = bytes[at].toInt() and 255
            at += 1
            if (scanning && (marker == 0 || marker in 0xd0..0xd7)) continue
            if (marker == 0xd9) return at
            if (marker == 0xd8 || marker == 0x01 || at + 2 > bytes.size) {
                throw IllegalArgumentException("Malformed JPEG marker")
            }
            val length = ((bytes[at].toInt() and 255) shl 8) or (bytes[at + 1].toInt() and 255)
            if (length < 2 || at + length > bytes.size) throw IllegalArgumentException("Malformed JPEG segment")
            at += length
            scanning = marker == 0xda
        }
        throw IllegalArgumentException("Missing JPEG EOI")
    }

    /**
     * Tag numbers inside the file's Apple MakerNote: the Exif item payload ("Exif\0\0" +
     * TIFF, optionally behind an exif_data_block offset) is walked for tag 0x927c, whose
     * "Apple iOS" payload entries carry the Live pairing (17) and style ids (43, 84).
     */
    private data class IfdEntry(val tag: Int, val type: Int, val count: Int, val value: Int)

    fun makerNoteTags(exifPayload: ByteArray): List<Int> {
        val marker = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1)
        fun hasMarker(at: Int) = exifPayload.size >= at + marker.size &&
            (0 until marker.size).all { exifPayload[at + it] == marker[it] }
        // Two storages exist: a bare APP1 body and the ISO exif_data_block, whose four
        // byte offset field sits in front of the APP1 body. The TIFF starts after both.
        val tiff = when {
            exifPayload.size > 10 && hasMarker(4) -> exifPayload.copyOfRange(10, exifPayload.size)
            hasMarker(0) -> exifPayload.copyOfRange(6, exifPayload.size)
            else -> return emptyList()
        }
        if (tiff.size < 14) return emptyList()
        val bigEndian = tiff[0] == 0x4d.toByte()
        if (!bigEndian && !(tiff[0] == 0x49.toByte() && tiff[1] == 0x49.toByte())) return emptyList()
        fun u16(at: Int): Int = if (bigEndian) ((tiff[at].toInt() and 255) shl 8) or (tiff[at + 1].toInt() and 255)
        else ((tiff[at + 1].toInt() and 255) shl 8) or (tiff[at].toInt() and 255)
        fun u32(at: Int): Int = if (bigEndian) (u16(at) shl 16) or u16(at + 2)
        else (u16(at + 2) shl 16) or u16(at)
        if (u16(2) != 42) return emptyList()
        fun entries(at: Int): List<IfdEntry>? {
            if (at < 8 || at > tiff.size - 6) return null
            val count = u16(at)
            if (count > 512 || at + 6 + count * 12 > tiff.size) return null
            return List(count) { index ->
                val entry = at + 2 + index * 12
                IfdEntry(u16(entry), u16(entry + 2), u32(entry + 4), u32(entry + 8))
            }
        }
        val root = entries(u32(4)) ?: return emptyList()
        val pointer = root.firstOrNull { it.tag == 0x8769 } ?: return emptyList()
        val exifIfd = entries(pointer.value) ?: return emptyList()
        val note = exifIfd.firstOrNull { it.tag == 0x927c } ?: return emptyList()
        if (note.type != 7 || note.count <= 0 || note.value <= 0 || note.value + note.count > tiff.size) return emptyList()
        val noteBytes = tiff.copyOfRange(note.value, note.value + note.count)
        if (noteBytes.size < 18) return emptyList()
        if (!noteBytes.copyOfRange(0, 12).toString(Charsets.ISO_8859_1).startsWith("Apple iOS")) return emptyList()
        if (noteBytes[12] != 0x4d.toByte() || noteBytes[13] != 0x4d.toByte()) return emptyList()
        val count = ((noteBytes[14].toInt() and 255) shl 8) or (noteBytes[15].toInt() and 255)
        if (count <= 0 || count > 512 || 16 + count * 12 > noteBytes.size) return emptyList()
        return List(count) { index ->
            val at = 16 + index * 12
            ((noteBytes[at].toInt() and 255) shl 8) or (noteBytes[at + 1].toInt() and 255)
        }
    }
}
