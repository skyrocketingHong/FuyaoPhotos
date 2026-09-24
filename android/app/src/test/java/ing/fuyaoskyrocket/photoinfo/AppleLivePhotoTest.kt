package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.*
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportOptions
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class AppleLivePhotoTest {
    private val identifier = "FD286677-3BE8-4385-900E-DA3496641B03"
    private fun box(type: String, vararg bytes: ByteArray) = IsoBmff.box(type, *bytes)
    private fun data(block: java.io.DataOutputStream.() -> Unit) = IsoBmff.data(block)
    private fun full(type: String, bytes: ByteArray) = IsoBmff.full(type, payload = bytes)
    private fun movie(): ByteArray = box("ftyp", "isom\u0000\u0000\u0000\u0000isom".toByteArray()) +
        box("mdat", ByteArray(128) { it.toByte() }) + box("moov",
            full("mvhd", data { writeLong(0); writeInt(600); writeInt(1800); write(ByteArray(80)) }),
            box("trak", full("tkhd", data { writeLong(0); writeInt(1); write(ByteArray(68)) }),
                box("mdia", full("hdlr", data { writeInt(0); writeBytes("vide"); write(ByteArray(12)) }))))

    @Test fun movieSamplesStayAtTheirOriginalOffsetsAndPairKeyIsWritten() {
        val source = File.createTempFile("live-source", ".mp4")
        val output = File.createTempFile("live-output", ".mov")
        try {
            val original = movie(); source.writeBytes(original)
            AppleLivePhotoMovie.write(source, output, identifier, 1_000_000)
            val result = output.readBytes()
            val mdat = IsoBmff.boxes(original).single { it.type == "mdat" }
            assertArrayEquals(original.copyOfRange(mdat.payload, mdat.end), result.copyOfRange(mdat.payload, mdat.end))
            assertEquals(1, IsoBmff.boxes(result).count { it.type == "moov" })
            assertTrue(result.toString(Charsets.ISO_8859_1).contains(identifier))
            assertTrue(result.toString(Charsets.ISO_8859_1).contains("com.apple.quicktime.still-image-time"))
            assertThrows(IllegalArgumentException::class.java) { AppleLivePhotoMovie.write(source, output, identifier, 3_000_000) }
        } finally { source.delete(); output.delete() }
    }

    @Test fun pairingNoteUsesAppleOffsetsAndDoesNotInventCameraMetadata() {
        val exif = ApplePhotoMetadata.withIdentifier(null, identifier)
        val text = exif.toString(Charsets.ISO_8859_1)
        assertTrue(text.contains("Apple iOS")); assertTrue(text.contains(identifier))
        assertFalse(text.contains("iPhone"))
        val at = text.indexOf("Apple iOS")
        assertEquals(32, ByteBuffer.wrap(exif).getInt(at + 24))
        assertEquals(identifier, String(exif, at + 32, 36, Charsets.US_ASCII))
        assertThrows(IllegalArgumentException::class.java) { ApplePhotoMetadata.withIdentifier(null, "invalid") }
    }

    @Test fun privateMotionSampleCanProduceOraclePairWithoutChangingMedia() {
        val root = System.getenv("FUYAO_FORMAT_ORACLE_DIR")?.let(::File) ?: return
        val input = System.getenv("FUYAO_MOTION_SAMPLE")?.let(::File) ?: return
        val original = requireNotNull(MotionPhoto.inspect(input, "image/jpeg").motion)
        val video = File(root, "source.mp4")
        VideoMetadata.copy(input, original.offset, original.length, video, ExportOptions(keepLocation = false))
        AppleLivePhotoMovie.write(video, File(root, "paired.mov"), identifier, original.timestampUs)
        val bytes = requireNotNull(javaClass.getResourceAsStream("/media/base.heic")).use { it.readBytes() }
        val image = HeifImageContainer.read(bytes)
        val exif = ApplePhotoMetadata.withIdentifier(null, identifier)
        val exifID = image.items.maxOf { it.id } + 1
        image.copy(items = image.items + HeifImageContainer.Item(exifID, "Exif", byteArrayOf(0),
            data { writeInt(6); write(exif) }),
            references = image.references + HeifImageContainer.Reference("cdsc", exifID, listOf(image.primary)))
            .write(File(root, "paired.heic"))
    }
}
