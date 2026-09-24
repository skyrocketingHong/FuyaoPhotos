package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.IsoGainMapMetadata
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class IsoGainMapMetadataTest {
    @Test fun monochromeMetadataRetainsDisplayHeadroom() {
        val map = IsoGainMapMetadata.fromRatios(
            floatArrayOf(1f, 1f, 1f), floatArrayOf(4f, 4f, 4f),
            floatArrayOf(1f, 1f, 1f), FloatArray(3), FloatArray(3), 1f, 4f,
        )
        val bytes = map.strictToneMap()
        val encoded = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        assertEquals(1, map.channelCount)
        assertEquals(62, bytes.size)
        assertEquals(0x40, bytes[5].toInt() and 0xff)
        assertEquals(0, encoded.getInt(6))
        assertEquals(200_000, encoded.getInt(14))
        assertEquals(200_000, encoded.getInt(30))
        assertEquals(100_000, encoded.getInt(34))
    }

    @Test fun chromaticMetadataKeepsEachChannel() {
        val map = IsoGainMapMetadata.fromRatios(
            floatArrayOf(1f, 1f, 1f), floatArrayOf(2f, 4f, 8f),
            floatArrayOf(1f, 1f, 1f), FloatArray(3), FloatArray(3), 1f, 8f,
        )
        val bytes = map.strictToneMap()
        val encoded = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        assertEquals(3, map.channelCount)
        assertEquals(142, bytes.size)
        assertEquals(0xc0, bytes[5].toInt() and 0xff)
        assertEquals(100_000, encoded.getInt(30))
        assertEquals(200_000, encoded.getInt(70))
        assertEquals(300_000, encoded.getInt(110))
    }

    @Test fun invalidGainMapValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            IsoGainMapMetadata.fromRatios(
                floatArrayOf(0f, 1f, 1f), floatArrayOf(4f, 4f, 4f),
                floatArrayOf(1f, 1f, 1f), FloatArray(3), FloatArray(3), 1f, 4f,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            IsoGainMapMetadata.fromRatios(
                floatArrayOf(1f, 1f, 1f), floatArrayOf(4f, 4f, 4f),
                floatArrayOf(1f, 1f, 1f), FloatArray(3), FloatArray(3), 4f, 2f,
            )
        }
    }
}
