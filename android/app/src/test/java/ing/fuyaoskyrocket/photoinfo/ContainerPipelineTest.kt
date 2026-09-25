package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.HeifImageContainer
import ing.fuyaoskyrocket.photoinfo.domain.media.IsoGainMapMetadata
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * Replays the exact container chain the ten-bit export runs - assembler output, colour
 * description, gain map attach, style layer, motion directory - and asserts the primary
 * keeps its properties and the file keeps exactly one tone map at every hop.
 */
class ContainerPipelineTest {
    private fun ispe(width: Int, height: Int): ByteArray = box("ispe") {
        writeInt(width); writeInt(height)
    }
    private fun pixi(bits: IntArray): ByteArray = box("pixi") {
        write(bits.size); bits.forEach(::write)
    }
    private fun hvc(): ByteArray = box("hvcC") {
        write(1); repeat(15) { write(2) }; writeShort(0); write(0xFC); write(0xFD)
        write(0xFA); write(0xFA); writeShort(0); write(0x0B); write(0)
    }
    private fun gainHvc(): ByteArray = box("hvcC") {
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

    private fun assembledBase(): HeifImageContainer = HeifImageContainer(
        false, 1,
        listOf(HeifImageContainer.Item(1, "hvc1", byteArrayOf(0),
            byteArrayOf(0, 0, 0, 4, 0x26, 1, 0xaa.toByte(), 0xbb.toByte()),
            listOf(
                HeifImageContainer.Property(1, true),
                HeifImageContainer.Property(2, false),
                HeifImageContainer.Property(3, true)))),
        listOf(ispe(3072, 4096), pixi(intArrayOf(10, 10, 10)), hvc()), emptyList())

    private fun plainGain(): HeifImageContainer = HeifImageContainer(
        false, 7,
        listOf(HeifImageContainer.Item(7, "hvc1", byteArrayOf(0),
            byteArrayOf(0, 0, 0, 4, 0x26, 1, 0xcc.toByte(), 0xdd.toByte()),
            listOf(HeifImageContainer.Property(1, true), HeifImageContainer.Property(2, true)))),
        listOf(ispe(1536, 2048), gainHvc()), emptyList())

    @Test fun primaryKeepsPropertiesThroughTheWholeChain() {
        val file = File.createTempFile("pipeline-", ".heic")
        try {
            val metadata = IsoGainMapMetadata.fromRatios(
                FloatArray(3) { 1f }, floatArrayOf(2f, 2f, 2f), floatArrayOf(1f, 1f, 1f),
                FloatArray(3) { 0f }, FloatArray(3) { 0f }, 1f, 4f)

            fun checkStage(label: String, container: HeifImageContainer) {
                val primaryItem = container.items.single { it.id == container.primary }
                val types = primaryItem.properties.map {
                    String(container.properties[it.index - 1], 4, 4, Charsets.US_ASCII) }
                assertTrue("$label lost primary properties: $types", types.contains("ispe"))
                assertTrue("$label lost hvcC: $types", types.contains("hvcC"))
                assertEquals("$label tone map count", 1, container.items.count { it.type == "tmap" })
            }

            var container = assembledBase().withExif(null)
            container = container.withColorSpace(9, 13)
            container = container.withGainMap(plainGain(), metadata)
            checkStage("gain", container)
            container.write(file)
            container = HeifImageContainer.read(file)
            checkStage("gain round trip", container)
            container = container.withAuxiliary(plainGain(),
                "urn:mpeg:hevc:2015:auxid:2", "<x/>")
            checkStage("disparity", container)
            container = container.withPhotographicStyles(2160, 2880, true, null, null)
            checkStage("styles", container)
            container = container.withMotionDirectory(5000, "video/mp4", 1000)
            checkStage("motion", container)
            container.write(file)

            val final = HeifImageContainer.read(file)
            val primary = final.items.single { it.id == final.primary }
            assertTrue("primary lost properties: ${primary.properties}",
                primary.properties.any { String(final.properties[it.index - 1], 4, 4, Charsets.US_ASCII) == "ispe" })
            assertTrue(primary.properties.any { String(final.properties[it.index - 1], 4, 4, Charsets.US_ASCII) == "hvcC" })
            assertEquals("tone map count", 1, final.items.count { it.type == "tmap" })
            val ispePropertyIndex = primary.properties.map(HeifImageContainer.Property::index)
                .firstOrNull { index: Int ->
                    String(final.properties[index - 1], 4, 4, Charsets.US_ASCII) == "ispe" }
            assertNotNull("primary lost ispe", ispePropertyIndex)
            val ispeProp = final.properties[requireNotNull(ispePropertyIndex) - 1]
            assertEquals(3072, ((ispeProp[14].toInt() and 255) shl 8) or (ispeProp[15].toInt() and 255))
            assertEquals(4096, ((ispeProp[18].toInt() and 255) shl 8) or (ispeProp[19].toInt() and 255))
        } finally { file.delete() }
    }
}
