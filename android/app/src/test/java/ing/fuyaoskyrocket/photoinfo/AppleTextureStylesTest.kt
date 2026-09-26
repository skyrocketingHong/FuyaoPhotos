package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.AppleDepthRendering
import ing.fuyaoskyrocket.photoinfo.domain.media.ApplePortraitMetadata
import ing.fuyaoskyrocket.photoinfo.domain.media.AppleTextureStyles
import ing.fuyaoskyrocket.photoinfo.domain.media.HeifImageContainer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

/**
 * Photographic Styles 3 and the depth calibration payloads: the textureInfo plist
 * contract, the twelve-matte container layer, and the REND rendering parameters that
 * gate depth editing in Apple Photos.
 */
class AppleTextureStylesTest {
    private fun ispe(width: Int, height: Int): ByteArray = box("ispe") {
        writeInt(width); writeInt(height)
    }
    private fun hvc(): ByteArray = box("hvcC") {
        write(1); repeat(15) { write(2) }; writeShort(0); write(0xFC); write(0xFD)
        write(0xF8); write(0xF8); writeShort(0); write(0x0B); write(0)
    }
    private fun box(type: String, body: java.io.DataOutputStream.() -> Unit): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val stream = java.io.DataOutputStream(out)
        stream.writeInt(0)
        stream.writeBytes(type)
        stream.writeInt(0)
        stream.body()
        stream.flush()
        val bytes = out.toByteArray()
        bytes[3] = bytes.size.toByte()
        return bytes
    }

    @Test fun textureInfoCarriesTheNativeContract() {
        val payload = AppleTextureStyles.textureInfoPayload(104)
        assertEquals("bplist00", String(payload, 0, 8, Charsets.US_ASCII))
        val text = payload.toString(Charsets.US_ASCII)
        listOf("Preset", "Standard", "CaptureType", "LF", "CaptureMode", "Still", "PortType",
            "PortTypeBack", "HardwareModel", "iPhone 18 Pro", "TextureStylePeopleDataVersion",
            "FilmGrainSeed").forEach { key ->
            assertTrue("textureInfo lacks $key", text.contains(key))
        }
        // The people-data version and the grain seed are 8-bit inline integers in the
        // observed payload; both must appear after their key strings.
        assertTrue(payload.size in 150..400)
        val three = byteArrayOf(0x10, 3)
        assertTrue("people data version 3 missing", contains(payload, three))
        assertTrue("grain seed 104 missing", contains(payload, byteArrayOf(0x10, 104)))
    }

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        outer@ for (at in 0..haystack.size - needle.size) {
            for (i in needle.indices) if (haystack[at + i] != needle[i]) continue@outer
            return true
        }
        return false
    }

    @Test fun grainSeedIsStableAndBounded() {
        assertEquals(AppleTextureStyles.grainSeedFor("IMG_1234.jpg"), AppleTextureStyles.grainSeedFor("IMG_1234.jpg"))
        assertTrue(AppleTextureStyles.grainSeedFor("a") in 0..0x7fffffffL)
        assertNotEquals(AppleTextureStyles.grainSeedFor("IMG_1.jpg"), AppleTextureStyles.grainSeedFor("IMG_2.jpg"))
    }

    @Test fun textureLayerAttachesTwelveMattesAndTheUriItem() {
        val base = HeifImageContainer(
            false, 1,
            listOf(HeifImageContainer.Item(1, "hvc1", byteArrayOf(0),
                byteArrayOf(0, 0, 0, 4, 0x26, 1, 0xaa.toByte(), 0xbb.toByte()),
                listOf(
                    HeifImageContainer.Property(1, true),
                    HeifImageContainer.Property(2, false),
                    HeifImageContainer.Property(3, true)))),
            listOf(ispe(4096, 3072), box("pixi") { write(3); write(8); write(8); write(8) }, hvc()), emptyList())
            .withGainMap(
                HeifImageContainer(false, 7,
                    listOf(HeifImageContainer.Item(7, "hvc1", byteArrayOf(0),
                        byteArrayOf(0, 0, 0, 4, 0x26, 1, 0xcc.toByte(), 0xdd.toByte()),
                        listOf(HeifImageContainer.Property(1, true)))),
                    listOf(ispe(2048, 1536)), emptyList()),
                ing.fuyaoskyrocket.photoinfo.domain.media.IsoGainMapMetadata.fromRatios(
                    FloatArray(3) { 1f }, floatArrayOf(2f, 2f, 2f), floatArrayOf(1f, 1f, 1f),
                    FloatArray(3) { 0f }, FloatArray(3) { 0f }, 1f, 4f))

        val matte = ByteArray(64) { (it * 7).toByte() }
        val styled = base.withPhotographicStyles(2880, 2160, true, null, null)
            .withTextureStyles(AppleTextureStyles.textureInfoPayload(7), hvc(), matte)
        val file = File.createTempFile("texture-", ".heic")
        try {
            styled.write(file)
            val read = HeifImageContainer.read(file)
            assertEquals("tone map count", 1, read.items.count { it.type == "tmap" })
            val tone = read.items.single { it.type == "tmap" }.id
            val primary = read.primary

            val textureItems = read.items.filter { it.type == "uri " }
            assertEquals(2, textureItems.size)
            val texture = textureItems.single { item ->
                item.infoSuffix.includes(AppleTextureStyles.TEXTURE_STYLES_CONTENT_TYPE) }
            assertEquals("metadata", String(texture.infoSuffix).substringBefore('\u0000'))

            val mattes = read.items.filter { item ->
                item.properties.any { property ->
                    val raw = read.properties[property.index - 1]
                    String(raw, 4, 4, Charsets.US_ASCII) == "auxC" &&
                        AppleTextureStyles.SEMANTIC_MATTE_URNS.any { urn ->
                            raw.includes(urn) } } }
            assertEquals(12, mattes.size)
            mattes.forEach { item ->
                assertEquals("hvc1", item.type)
                assertArrayEquals("mattes share one frame", matte, item.payload)
                val ispeProp = item.properties.map { read.properties[it.index - 1] }
                    .single { String(it, 4, 4, Charsets.US_ASCII) == "ispe" }
                assertEquals(AppleTextureStyles.MATTE_WIDTH, ((ispeProp[14].toInt() and 255) shl 8) or (ispeProp[15].toInt() and 255))
                assertEquals(AppleTextureStyles.MATTE_HEIGHT, ((ispeProp[18].toInt() and 255) shl 8) or (ispeProp[19].toInt() and 255))
                val pixiProp = item.properties.map { read.properties[it.index - 1] }
                    .single { String(it, 4, 4, Charsets.US_ASCII) == "pixi" }
                assertEquals(1, pixiProp[12].toInt() and 255)
                assertEquals(8, pixiProp[13].toInt() and 255)
            }

            val auxl = read.references.filter { it.type == "auxl" && mattes.any { m -> m.id == it.from } }
            assertEquals(12, auxl.size)
            auxl.forEach { assertEquals(listOf(primary, tone), it.to) }
            assertEquals(listOf(primary, tone), read.references.single { it.type == "cdsc" && it.from == texture.id }.to)
            assertEquals(12, AppleTextureStyles.SEMANTIC_MATTE_URNS.map { urn ->
                read.properties.count { raw -> raw.includes(urn) } }.sum())
        } finally { file.delete() }
    }

    private fun ByteArray.includes(text: String): Boolean {
        val needle = text.toByteArray(Charsets.US_ASCII)
        outer@ for (at in 0..size - needle.size) {
            for (i in needle.indices) if (this[at + i] != needle[i]) continue@outer
            return true
        }
        return false
    }

    @Test fun rendTemplatePatchesOnlyTheDynamicRecords() {
        val neutral = AppleDepthRendering.renderingParameters(0.0, 0.0)
        assertEquals("REND", String(neutral, 0, 4, Charsets.US_ASCII))
        val order = ByteOrder.LITTLE_ENDIAN
        val declared = ByteBuffer.wrap(neutral).order(order).getInt(8)
        assertEquals(neutral.size, declared)
        val active = AppleDepthRendering.renderingParameters(1.0, 2.0)
        assertEquals(neutral.size, active.size)
        var records = 0
        var changed = 0
        var cursor = 16
        val dynamic = setOf(0x0190, 0x0191, 0x0192, 0x0193, 0x01c2, 0x01c3, 0x01c4, 0x01c5)
        while (cursor + 8 <= declared) {
            val id = ByteBuffer.wrap(active, cursor, 2).order(order).short.toInt() and 0xffff
            if (neutral.copyOfRange(cursor, cursor + 8).contentEquals(active.copyOfRange(cursor, cursor + 8))) {
                assertFalse("static record 0x${id.toString(16)} changed", id in dynamic)
            } else {
                assertTrue("unexpected record 0x${id.toString(16)} changed", id in dynamic)
                changed++
            }
            cursor += 8; records++
        }
        assertEquals(167, records)
        assertEquals(8, changed)
        // Full activation writes the known XHLRB extremes: 50 and headroom 2.
        assertEquals(50, recordValue(active, 0x0190))
        assertEquals(2.0f, Float.fromBits(recordValue(active, 0x01c5)))
    }

    private fun recordValue(blob: ByteArray, id: Int): Int {
        val order = ByteOrder.LITTLE_ENDIAN
        var cursor = 16
        while (cursor + 8 <= blob.size) {
            if (ByteBuffer.wrap(blob, cursor, 2).order(order).short.toInt() and 0xffff == id)
                return ByteBuffer.wrap(blob, cursor + 4, 4).order(order).int
            cursor += 8
        }
        error("record missing")
    }

    @Test fun depthSidecarCarriesTheFullCalibrationBlock() {
        val calibration = ApplePortraitMetadata.Calibration(
            mainWidth = 4096, mainHeight = 3072, auxWidth = 2048, auxHeight = 1536,
            focalLength35mm = 23.0, focalLengthMm = 8.7, headroomStops = 1.5)
        val xmp = ApplePortraitMetadata.disparityXmp(1.67, calibration)
        val rend = Regex("<depthBlurEffect:RenderingParameters>([^<]+)</depthBlurEffect:RenderingParameters>")
            .find(xmp)!!.groupValues[1]
        assertEquals("REND", String(java.util.Base64.getDecoder().decode(rend), 0, 4, Charsets.US_ASCII))
        assertTrue(xmp.contains("<depthBlurEffect:SimulatedAperture>1.67</depthBlurEffect:SimulatedAperture>"))
        assertTrue(xmp.contains("IntrinsicMatrixReferenceWidth>2048"))
        assertTrue(xmp.contains("IntrinsicMatrixReferenceHeight>1536"))
        assertTrue(xmp.contains("portraitLightingEffect/2.0/"))
        // The focal model scales with the aux plane: 4096 * 23 / 36 * 0.5 = 1308.44.
        assertTrue(xmp.contains("1308."))
        assertFalse("calibration produced NaN", xmp.contains("NaN"))
    }

    @Test fun depthSidecarSurvivesMissingFocalMetadata() {
        val calibration = ApplePortraitMetadata.Calibration(
            mainWidth = 100, mainHeight = 100, auxWidth = 50, auxHeight = 50,
            focalLength35mm = null, focalLengthMm = null, headroomStops = 0.0)
        val xmp = ApplePortraitMetadata.disparityXmp(null, calibration)
        assertFalse(xmp.contains("IntrinsicMatrixReferenceWidth"))
        assertTrue(xmp.contains("<depthBlurEffect:RenderingParameters>"))
        assertFalse(xmp.contains("SimulatedAperture"))
        assertFalse("calibration produced NaN", xmp.contains("NaN"))
    }
}
