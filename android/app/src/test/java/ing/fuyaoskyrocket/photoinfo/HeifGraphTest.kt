package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.HeifGraph
import ing.fuyaoskyrocket.photoinfo.domain.media.MotionPhoto
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class HeifGraphTest {
    private fun box(type: String, payload: ByteArray): ByteArray = ByteBuffer.allocate(payload.size + 8)
        .putInt(payload.size + 8).put(type.toByteArray(Charsets.US_ASCII)).put(payload).array()

    private fun full(type: String, version: Int, payload: ByteArray): ByteArray =
        box(type, byteArrayOf(version.toByte(), 0, 0, 0) + payload)

    private fun u16(value: Int): ByteArray = byteArrayOf((value ushr 8).toByte(), value.toByte())

    private fun infe(id: Int, type: String, contentType: String? = null): ByteArray =
        full("infe", 2, u16(id) + u16(0) + type.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0) + (contentType?.toByteArray(Charsets.US_ASCII) ?: byteArrayOf()))

    private fun fixture(
        aux: String? = "urn:iso:std:iso:ts:21496:-1",
        referenceTarget: Int = 1,
        toneMap: Boolean = true,
        style: Boolean = false,
        motion: Boolean = false,
    ): ByteArray {
        val primary = infe(1, "hvc1")
        val items = listOf(primary) + (if (toneMap) listOf(infe(2, "tmap")) else emptyList()) +
            (if (style) listOf(infe(3, "mime", "application/x-apple-styleMetadata")) else emptyList()) +
            (if (motion) listOf(infe(4, "mime", "application/rdf+xml")) else emptyList())
        val iinf = full("iinf", 0, u16(items.size) + items.fold(ByteArray(0)) { result, item -> result + item })
        val iref = if (toneMap) full("iref", 0, box("dimg", u16(2) + u16(1) + u16(referenceTarget))) else byteArrayOf()
        val iprp = aux?.let { box("iprp", box("ipco", full("auxC", 0, it.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)))) } ?: byteArrayOf()
        val meta = full("meta", 0, full("pitm", 0, u16(1)) + iinf + iref + iprp)
        val still = box("ftyp", "heic".toByteArray() + byteArrayOf(0, 0, 0, 0) + "mif1".toByteArray()) +
            meta + box("mdat", ByteArray(16))
        val video = box("ftyp", "isom".toByteArray() + byteArrayOf(0, 0, 0, 0)) +
            box("mdat", ByteArray(16)) + box("moov", ByteArray(8))
        return if (motion) still + box("mpvd", video) else still
    }

    private fun inspect(bytes: ByteArray): HeifGraph.Report {
        val file = File.createTempFile("heif-graph-", ".heic")
        try { file.writeBytes(bytes); return HeifGraph.inspect(file) }
        finally { file.delete() }
    }

    @Test fun isoGraphRequiresToneMapAuxiliaryAndReferenceToPrimary() {
        val valid = inspect(fixture(style = true))
        assertEquals(1, valid.primaryItemId)
        assertEquals(2, valid.toneMapItemId)
        assertTrue(valid.hasIsoGainMap)
        assertTrue(valid.hasStyleMetadata)
        assertTrue(valid.hasUnsupportedItems)
        assertFalse(valid.hasPortraitMetadata)
        assertFalse(inspect(fixture(aux = "unrelated")).hasIsoGainMap)
        assertFalse(inspect(fixture(toneMap = false)).hasIsoGainMap)
        assertTrue(inspect(fixture(toneMap = false, aux = "unrelated")).hasUnsupportedItems)
        val ordinary = inspect(fixture(toneMap = false, aux = null))
        assertFalse(ordinary.hasUnsupportedItems)
    }

    @Test fun aDanglingDimgReferenceDoesNotPass() {
        assertFalse(inspect(fixture(referenceTarget = 900)).hasIsoGainMap)
    }

    @Test fun randomTextInMediaDataDoesNotFabricateGraph() {
        val bytes = box("ftyp", "heic".toByteArray() + byteArrayOf(0, 0, 0, 0)) +
            box("mdat", "tmap urn:iso:std:iso:ts:21496:-1".toByteArray())
        assertFails { inspect(bytes) }
    }

    @Test fun truncatedAndOversizedBoxesFailClosed() {
        val valid = fixture()
        assertFails { inspect(valid.copyOf(valid.size - 4)) }
        val oversized = valid.copyOf()
        ByteBuffer.wrap(oversized).putInt(Int.MAX_VALUE)
        assertFails { inspect(oversized) }
    }

    @Test fun plainHeicFromSystemEncoderHasNoAuxiliaryMetadata() {
        val file = File.createTempFile("heif-system-", ".heic")
        try {
            javaClass.getResourceAsStream("/heif/ordinary-64.heic")!!.use { input ->
                file.outputStream().use(input::copyTo)
            }
            val report = HeifGraph.inspect(file)
            assertFalse(report.hasUnsupportedItems)
            assertFalse(report.hasIsoGainMap)
            assertNull(report.toneMapItemId)
        } finally { file.delete() }
    }

    @Test fun heicMotionRequiresMatchingDirectoryAndTrailingVideo() {
        val file = File.createTempFile("heif-motion-", ".heic")
        try {
            file.writeBytes(fixture(aux = null, toneMap = false, motion = true))
            val graph = HeifGraph.inspect(file)
            assertFalse(graph.hasUnsupportedItems)
            val payload = requireNotNull(graph.motionPayload)
            val xmp = """<x:xmpmeta xmlns:x="adobe:ns:meta/" xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:Camera="http://ns.google.com/photos/1.0/camera/" xmlns:Container="http://ns.google.com/photos/1.0/container/" xmlns:Item="http://ns.google.com/photos/1.0/container/item/"><rdf:RDF><rdf:Description Camera:MotionPhoto="1" Camera:MotionPhotoVersion="1"><Container:Directory><rdf:Seq><rdf:li><Container:Item Item:Mime="image/heic" Item:Semantic="Primary" Item:Length="0" Item:Padding="8"/></rdf:li><rdf:li><Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="${payload.length}"/></rdf:li></rdf:Seq></Container:Directory></rdf:Description></rdf:RDF></x:xmpmeta>"""
            val video = MotionPhoto.inspectHeif(file, xmp, payload)
            assertEquals(payload.offset, video.offset)
            assertEquals(payload.length, video.length)
            assertThrows(IllegalArgumentException::class.java) {
                MotionPhoto.inspectHeif(file, xmp.replace("Item:Padding=\"8\"", "Item:Padding=\"0\""), payload)
            }
            assertThrows(IllegalArgumentException::class.java) {
                MotionPhoto.inspectHeif(file, xmp.replace("Item:Length=\"${payload.length}\"", "Item:Length=\"1\""), payload)
            }
            assertThrows(IllegalArgumentException::class.java) { MotionPhoto.inspectHeif(file, null, payload) }
            file.appendBytes(box("free", byteArrayOf()))
            assertFails { HeifGraph.inspect(file) }
        } finally { file.delete() }
    }

    private fun assertFails(action: () -> Any?) {
        try { action(); fail("Malformed HEIF accepted") }
        catch (_: IOException) { }
    }
}
