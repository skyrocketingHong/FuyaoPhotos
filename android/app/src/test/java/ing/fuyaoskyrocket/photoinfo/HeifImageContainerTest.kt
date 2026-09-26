package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HeifImageContainerTest {
    @Test fun missingMuxerColorBoxGetsExplicitEncoderColorDescription() {
        val base=HeifImageContainer.read(fixture("base"))
        val kept=base.properties.indices.filter { String(base.properties[it],4,4,Charsets.US_ASCII)!="colr" }
        val mapping=kept.mapIndexed { new,old -> old+1 to new+1 }.toMap()
        val without=base.copy(properties=kept.map { base.properties[it] },items=base.items.map { item ->
            item.copy(properties=item.properties.mapNotNull { p -> mapping[p.index]?.let { p.copy(index=it) } })
        })
        val result=without.withColorSpace(12,13)
        val file=File.createTempFile("muxer-color", ".heic")
        try {
            result.write(file)
            val checked=HeifImageContainer.read(file)
            checked.validateEditable()
            val index=checked.items.single { it.id==checked.primary }.properties.last().index
            val color=checked.properties[index-1]
            assertEquals(12,java.nio.ByteBuffer.wrap(color).getShort(12).toInt())
            assertEquals(6,java.nio.ByteBuffer.wrap(color).getShort(16).toInt())
            assertArrayEquals(base.items.first().payload,checked.items.first().payload)
        } finally { file.delete() }
    }
    private fun fixture(name: String) = requireNotNull(javaClass.getResourceAsStream("/media/$name.heic")).use { it.readBytes() }

    @Test fun motionDirectoryEmbedsTheTrailingVideoPayload() {
        val base = HeifImageContainer.read(fixture("base"))
        val video = IsoBmff.box("ftyp","isom0000".toByteArray())+IsoBmff.box("moov",byteArrayOf())+
            IsoBmff.box("mdat",ByteArray(100) { it.toByte() })
        val file = File.createTempFile("heif-motion", ".heic")
        try {
            val withDirectory = base.withMotionDirectory(4321, "video/mp4", video.size.toLong())
            // The full reader rejects trailing motion payloads on purpose, so capture first.
            val xmpItem = withDirectory.items
                .single { it.type == "mime" && it.payload.decodeToString().contains("MotionPhoto") }
            withDirectory.write(file)
            java.io.FileOutputStream(file, true).use { output ->
                output.write(IsoBmff.data { writeInt(8 + video.size); writeBytes("mpvd") })
                output.write(video)
            }
            val graph = HeifGraph.inspect(file)
            val payload = requireNotNull(graph.motionPayload)
            assertEquals(file.length() - video.size, payload.offset)
            assertEquals(video.size.toLong(), payload.length)
            assertEquals(8L, payload.headerBytes)
            val motion = MotionPhoto.inspectHeif(file, xmpItem.payload.decodeToString(), payload)
            assertEquals(4321L, motion.timestampUs)
            assertEquals(payload.offset, motion.offset)
            assertEquals(payload.length, motion.length)
            assertEquals("video/mp4", motion.mime)
        } finally { file.delete() }
    }
    private val metadata = IsoGainMapMetadata.fromRatios(FloatArray(3) { 1f }, floatArrayOf(4f, 8f, 16f),
        floatArrayOf(2f, 1f, .5f), FloatArray(3) { .01f }, FloatArray(3) { .02f }, 1f, 4f)

    @Test fun codecPayloadsSurviveOffsetRebuildingAndGainMapLinking() {
        val base = HeifImageContainer.read(fixture("base"))
        val gain = HeifImageContainer.read(fixture("gain"))
        val combined = base.withGainMap(gain, metadata)
        val file = File.createTempFile("heif-roundtrip", ".heic")
        try {
            combined.write(file)
            val actual = HeifImageContainer.read(file)
            actual.validateEditable()
            assertEquals(combined.primary, actual.primary)
            assertEquals(combined.items.size, actual.items.size)
            combined.items.zip(actual.items).forEach { (before, after) ->
                assertArrayEquals(before.payload, after.payload)
                assertEquals(before.properties, after.properties)
            }
            assertEquals(metadata, actual.gainMap()!!.second)
            assertEquals(listOf(.5f, 1f, 2f), metadata.gamma)
            System.getenv("FUYAO_FORMAT_ORACLE_DIR")?.let { directory ->
                file.copyTo(File(directory, "kotlin-hdr.heic"), overwrite = true)
                actual.standalone(actual.gainMap()!!.first).write(File(directory, "kotlin-gain.heic"))
                base.withAuxiliary(gain, ApplePortraitMetadata.DISPARITY, ApplePortraitMetadata.disparityXmp(2.8, ApplePortraitMetadata.Calibration(4096, 3072, 2048, 1536, 24.0, 9.0, 0.0)))
                    .write(File(directory, "kotlin-portrait.heic"))
            }
        } finally { file.delete() }
    }

    @Test fun truncatedBoxesAndInvalidItemOffsetsAreRejected() {
        val source = fixture("base")
        for (end in listOf(0, 7, 16, source.size - 1)) {
            assertThrows(Exception::class.java) { HeifImageContainer.read(source.copyOf(end)) }
        }
        val changed = source.copyOf()
        val meta = IsoBmff.boxes(changed).single { it.type == "meta" }
        val location = IsoBmff.boxes(changed, meta.payload + 4, meta.end).single { it.type == "iloc" }
        changed.fill(0xff.toByte(), location.payload + 4, location.end)
        assertThrows(Exception::class.java) { HeifImageContainer.read(changed) }
    }

    @Test fun duplicateIdsAndForeignAuxiliaryImagesAreRejected() {
        val base = HeifImageContainer.read(fixture("base"))
        val bogus = IsoBmff.full("auxC", payload = "urn:vendor:depth\u0000".toByteArray())
        assertThrows(IllegalArgumentException::class.java) { base.copy(properties = base.properties + listOf(bogus)).validateEditable() }
        val output = File.createTempFile("bad-heif", ".heic")
        try { assertThrows(IllegalArgumentException::class.java) { base.copy(items = base.items + base.items.first()).write(output) } }
        finally { output.delete() }
    }
}
