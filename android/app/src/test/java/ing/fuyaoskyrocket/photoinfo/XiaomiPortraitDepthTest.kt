package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater

class XiaomiPortraitDepthTest {
    private fun marker(name: String) = name.toByteArray().copyOf(16)
    private fun fixture(oversized: Boolean = false): ByteArray {
        val compressed = ByteArray(100)
        val deflater = Deflater()
        val raw = byteArrayOf(0, 60, 100, -1)
        val count = try { deflater.setInput(raw); deflater.finish(); deflater.deflate(compressed) } finally { deflater.end() }
        val payload = byteArrayOf(1) + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(count).array() + compressed.copyOf(count)
        val header = ByteBuffer.allocate(320).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(if (oversized) 100_000 else 2); putInt(2); putInt(20); putInt(20)
        }.array()
        val original = marker("MCBOKEHSOT") + marker("MCSAMPLINGAUX") + marker("MCBOKEHINTF") +
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(320).array() + header +
            marker("MCBOKEHDEPTH") + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.size).array() + payload
        XiaomiPortraitDepth.descramble(original)
        return byteArrayOf(-1, -40, -1, -39, -1, -40, -1, -39) + original + marker("MCBOKEHEOT")
    }

    @Test fun encryptedDepthIsDecodedAndConvertedToRelativeDisparity() {
        val result = XiaomiPortraitDepth.decode(fixture())
        assertEquals(2, result.disparity.width); assertEquals(2, result.disparity.height)
        assertArrayEquals(byteArrayOf(-1, -61, -101, 0), result.disparity.pixels)
        assertNull(result.matte)
    }

    @Test fun corruptBlocksAndUnboundedDimensionsAreRejected() {
        assertThrows(Exception::class.java) { XiaomiPortraitDepth.decode(fixture(true)) }
        val bytes = fixture(); bytes[bytes.size - 22] = (bytes[bytes.size - 22].toInt() xor 7).toByte()
        assertThrows(Exception::class.java) { XiaomiPortraitDepth.decode(bytes) }
        assertThrows(Exception::class.java) { XiaomiPortraitDepth.decode(fixture().dropLast(8).toByteArray()) }
    }

    @Test fun privateXiaomiSampleProducesFullDepthAndMattePlanes() {
        val sample = System.getenv("FUYAO_PORTRAIT_SAMPLE")?.let(::File) ?: return
        val media = MotionPhoto.inspect(sample, "image/jpeg")
        val result = XiaomiPortraitDepth.decode(XiaomiPortraitTail.read(sample, requireNotNull(media.portraitTail)))
        assertEquals(1024, result.disparity.width); assertEquals(768, result.disparity.height)
        assertEquals(786432, result.disparity.pixels.size)
        assertEquals(6,result.orientation)
        assertNotNull(result.matte)
        System.getenv("FUYAO_FORMAT_ORACLE_DIR")?.let { root ->
            File(root, "xiaomi-disparity.raw").writeBytes(result.disparity.pixels)
            File(root, "xiaomi-matte.raw").writeBytes(requireNotNull(result.matte).pixels)
            val tail=XiaomiPortraitTail.read(sample,requireNotNull(media.portraitTail))
            File(root,"xiaomi-unblurred.jpg").writeBytes(tail.copyOf(XiaomiPortraitTail.layout(tail).secondEnd))
            val plane=File(root,"xiaomi-plane.heic")
            val matte=File(root,"xiaomi-matte.heic")
            val base=File(root,"xiaomi-base.heic")
            if(plane.exists() && matte.exists() && base.exists()) {
                HeifImageContainer.read(base)
                    .withAuxiliary(HeifImageContainer.read(plane),ApplePortraitMetadata.DISPARITY,ApplePortraitMetadata.disparityXmp(null, ApplePortraitMetadata.Calibration(3072, 4096, 1536, 2048, 23.0, 8.7, 0.0)))
                    .withAuxiliary(HeifImageContainer.read(matte),ApplePortraitMetadata.MATTE,ApplePortraitMetadata.matteXmp)
                    .write(File(root,"xiaomi-apple-portrait.heic"))
            }
        }
    }
}
