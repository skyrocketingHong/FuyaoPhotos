package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.HeifGraph
import ing.fuyaoskyrocket.photoinfo.domain.media.IsoGainMapMetadata
import ing.fuyaoskyrocket.photoinfo.domain.media.MediaEnvelope
import ing.fuyaoskyrocket.photoinfo.domain.media.MediaMetadataReportReader
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMetadataReportTest {
    private fun box(type: String, payload: ByteArray): ByteArray = ByteBuffer.allocate(payload.size + 8)
        .putInt(payload.size + 8).put(type.toByteArray(Charsets.US_ASCII)).put(payload).array()

    private fun full(type: String, version: Int, payload: ByteArray): ByteArray =
        box(type, byteArrayOf(version.toByte(), 0, 0, 0) + payload)

    private fun u16(value: Int): ByteArray = byteArrayOf((value ushr 8).toByte(), value.toByte())

    private fun u32(value: Int): ByteArray = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(),
        (value ushr 8).toByte(), value.toByte())

    private fun infe(id: Int, type: String, contentType: String? = null): ByteArray =
        full("infe", 2, u16(id) + u16(0) + type.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0) + (contentType?.toByteArray(Charsets.US_ASCII) ?: byteArrayOf()))

    private fun appleMakerNoteExif(): ByteArray {
        val note = run {
            val identifier = "00112233-4455-6677-8899-AABBCCDDEEFF".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
            val tag84 = "bplist00dummy".toByteArray(Charsets.US_ASCII)
            var out = "Apple iOS\u0000\u0000\u0001MM".toByteArray(Charsets.ISO_8859_1)
            out += u16(2)
            fun entry(tag: Int, type: Int, count: Int, offset: Int) = u16(tag) + u16(type) + u32(count) + u32(offset)
            out += entry(43, 2, 37, 44)
            out += entry(84, 7, tag84.size, 44 + identifier.size)
            out += u32(0)
            out += identifier + tag84
            out
        }
        // MM TIFF: IFD0 -> Exif IFD -> MakerNote (type 7) pointing at the note payload.
        var tiff = byteArrayOf(0x4d, 0x4d, 0x00, 0x2a) + u32(8)
        tiff += u16(1) + u16(0x8769) + u16(4) + u32(1) + u32(26) + u32(0)
        tiff += u16(1) + u16(0x927c) + u16(7) + u32(note.size) + u32(44) + u32(0)
        tiff += note
        return "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff
    }

    private fun heicFixture(textureStyles: Boolean): Pair<ByteArray, ByteArray> {
        val toneMap = IsoGainMapMetadata.fromRatios(
            floatArrayOf(2f, 2f, 2f), floatArrayOf(4f, 4f, 4f), floatArrayOf(1f, 1f, 1f),
            FloatArray(3) { 0f }, FloatArray(3) { 0f }, 1f, 4f).strictToneMap()
        val exif = appleMakerNoteExif()
        val textureItem = if (textureStyles) listOf(infe(4, "uri ", "tag:apple.com,2026:photo:metadata:texture_styles")) else emptyList()
        val items = listOf(infe(1, "hvc1"), infe(2, "tmap"), infe(3, "Exif")) + textureItem
        val iinf = full("iinf", 0, u16(items.size) + items.fold(ByteArray(0)) { result, item -> result + item })
        val iref = full("iref", 0, box("cdsc", u16(3) + u16(1) + u16(1)))
        val iprp = box("iprp", box("ipco", full("pixi", 0, byteArrayOf(3, 10, 10, 10)) +
            box("colr", "nclx".toByteArray(Charsets.US_ASCII) + u16(12) + u16(13) + u16(9) + byteArrayOf(0x80.toByte()))) +
            full("ipma", 0, u32(1) + u16(1) + byteArrayOf(2) + byteArrayOf(1, 2)))
        fun build(tmapOffset: Int, exifOffset: Int): ByteArray {
            val locations = u16(2) +
                u16(2) + u16(0) + u16(1) + u32(tmapOffset) + u32(toneMap.size) +
                u16(3) + u16(0) + u16(1) + u32(exifOffset) + u32(exif.size)
            val meta = full("meta", 0, full("pitm", 0, u16(1)) + iinf + iref + iprp + full("iloc", 0, byteArrayOf(0x44, 0x00) + locations))
            return box("ftyp", "heic".toByteArray() + byteArrayOf(0, 0, 0, 0) + "mif1".toByteArray()) +
                meta + box("mdat", toneMap + exif)
        }
        val probe = build(0, 0)
        // mdat payload starts right after the box header, which probe.size already includes.
        val payloadBase = probe.size - toneMap.size - exif.size
        val positioned = build(payloadBase, payloadBase + toneMap.size)
        return positioned to exif
    }

    private fun inspect(bytes: ByteArray): HeifGraph.Report {
        val file = File.createTempFile("media-report-", ".heic")
        try { file.writeBytes(bytes); return HeifGraph.inspect(file) }
        finally { file.delete() }
    }

    @Test fun heicReportSurfacesContainerFactsAndMakerNoteTags() {
        val (bytes, exif) = heicFixture(textureStyles = true)
        val file = File.createTempFile("media-report-", ".heic")
        file.writeBytes(bytes)
        try {
            val graph = HeifGraph.inspect(file)
            assertTrue(graph.brands.contains("heic") && graph.brands.contains("mif1"))
            assertEquals(10, graph.primaryBitDepth)
            assertEquals(12, graph.colrPrimaries)
            assertEquals(13, graph.colrTransfer)
            assertTrue(graph.hasTextureStyles)
            assertArrayEquals(graph.exifPayload, exif)

            val report = MediaMetadataReportReader.read("image/heic", file,
                MediaEnvelope(jpeg = false), graph, null)
            val labels = report.sections.flatMap { section -> section.rows.map { it.label } }
            assertTrue(labels.contains(ing.fuyaoskyrocket.photoinfo.R.string.media_report_brands))
            assertTrue(labels.contains(ing.fuyaoskyrocket.photoinfo.R.string.media_report_bit_depth))
            assertTrue(labels.contains(ing.fuyaoskyrocket.photoinfo.R.string.media_report_color_gamut))
            assertTrue(labels.contains(ing.fuyaoskyrocket.photoinfo.R.string.media_report_hdr_standard))
            assertTrue(labels.contains(ing.fuyaoskyrocket.photoinfo.R.string.media_report_hdr_parameters))
            assertTrue(labels.contains(ing.fuyaoskyrocket.photoinfo.R.string.media_report_styles_standard))
            assertTrue(labels.contains(ing.fuyaoskyrocket.photoinfo.R.string.media_report_styles_texture))
            assertTrue(labels.contains(ing.fuyaoskyrocket.photoinfo.R.string.media_report_maker_note))
            val standardRow = report.sections.flatMap { it.rows }.first { it.label == ing.fuyaoskyrocket.photoinfo.R.string.media_report_styles_standard }
            assertEquals(ing.fuyaoskyrocket.photoinfo.R.string.media_report_value_none, standardRow.value)
            val textureRow = report.sections.flatMap { it.rows }.first { it.label == ing.fuyaoskyrocket.photoinfo.R.string.media_report_styles_texture }
            assertEquals(ing.fuyaoskyrocket.photoinfo.R.string.media_report_value_styles2026, textureRow.value)
            val makerRow = report.sections.flatMap { it.rows }.first { it.label == ing.fuyaoskyrocket.photoinfo.R.string.media_report_maker_note }
            assertEquals("43, 84", makerRow.text)
        } finally { file.delete() }
    }

    @Test fun jpegReportClassifiesUltraHdrAndGoogleMotion() {
        val xmp = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF " +
            "xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
            "<rdf:Description xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\" " +
            "xmlns:hdrgm=\"http://ns.adobe.com/hdr-gain-map/1.0/\" " +
            "GCamera:MotionPhoto=\"1\" hdrgm:HDRCapacityMin=\"0\" " +
            "hdrgm:HDRCapacityMax=\"2.4\" hdrgm:Gamma=\"1.1\"/></rdf:RDF></x:xmpmeta>"
        val app1 = byteArrayOf(0xff.toByte(), 0xe1.toByte()) + u16(xmp.length + 31) +
            "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII) + xmp.toByteArray(Charsets.US_ASCII)
        val mpf = byteArrayOf(0xff.toByte(), 0xe2.toByte()) + u16(10) + "MPF\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(4)
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte()) + app1 + mpf +
            byteArrayOf(0xff.toByte(), 0xda.toByte(), 0x00, 0x02) + ByteArray(64) +
            byteArrayOf(0xff.toByte(), 0xd9.toByte()) + ByteArray(1024)
        val file = File.createTempFile("media-report-", ".jpg")
        file.writeBytes(jpeg)
        try {
            val report = MediaMetadataReportReader.read("image/jpeg", file,
                MediaEnvelope(jpeg = true, hdrHint = true), null, xmp)
            val rows = report.sections.flatMap { it.rows }
            assertTrue(rows.any { it.label == ing.fuyaoskyrocket.photoinfo.R.string.media_report_hdr_standard &&
                it.value == ing.fuyaoskyrocket.photoinfo.R.string.media_report_value_ultrahdr })
            val parameters = rows.first { it.label == ing.fuyaoskyrocket.photoinfo.R.string.media_report_hdr_parameters }
            assertTrue(parameters.text!!.contains("headroom 0.00..2.40"))
            assertTrue(rows.any { it.label == ing.fuyaoskyrocket.photoinfo.R.string.media_report_motion_standard &&
                it.value == ing.fuyaoskyrocket.photoinfo.R.string.media_report_value_googlemotion })
            assertTrue(rows.any { it.label == ing.fuyaoskyrocket.photoinfo.R.string.media_report_trailing_video })
        } finally { file.delete() }
    }

    @Test fun makerNoteTagsSurviveBothExifFramings() {
        val payload = appleMakerNoteExif()
        assertEquals(listOf(43, 84), MediaMetadataReportReader.makerNoteTags(payload))
        val blocked = u32(6) + payload
        assertEquals(listOf(43, 84), MediaMetadataReportReader.makerNoteTags(blocked))
        assertEquals(emptyList<Int>(), MediaMetadataReportReader.makerNoteTags("Exif\u0000\u0000junk".toByteArray()))
    }
}
