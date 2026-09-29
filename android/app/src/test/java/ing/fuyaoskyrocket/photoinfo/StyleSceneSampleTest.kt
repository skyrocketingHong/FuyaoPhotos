package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.StyleSceneSample
import org.junit.Assert.*
import org.junit.Test

class StyleSceneSampleTest {
    @Test fun percentileInterpolatesOnSortedValues() {
        assertEquals(0.0, StyleSceneSample.percentile(doubleArrayOf(), 0.5), 0.0)
        assertEquals(0.0, StyleSceneSample.percentile(doubleArrayOf(0.0, 1.0, 2.0, 3.0), 0.0), 0.0)
        assertEquals(3.0, StyleSceneSample.percentile(doubleArrayOf(0.0, 1.0, 2.0, 3.0), 1.0), 0.0)
        assertEquals(1.5, StyleSceneSample.percentile(doubleArrayOf(0.0, 1.0, 2.0, 3.0), 0.5), 0.0)
        assertEquals(0.75, StyleSceneSample.percentile(doubleArrayOf(0.0, 1.0, 2.0, 3.0), 0.25), 0.0)
    }

    @Test fun srgbDecodeMatchesTheTransferFunction() {
        assertEquals(0.0, StyleSceneSample.srgbToLinear(0.0), 0.0)
        assertEquals(1.0, StyleSceneSample.srgbToLinear(1.0), 0.0)
        assertEquals(0.04045 / 12.92, StyleSceneSample.srgbToLinear(0.04045), 1e-12)
        assertEquals(0.21586050011389926, StyleSceneSample.srgbToLinear(128.0 / 255.0), 1e-9)
    }

    @Test fun linearLumaWeightsRec709Channels() {
        val linear = StyleSceneSample.srgbLinearTable()
        assertEquals(1.0, StyleSceneSample.linearLuma(255, 255, 255, linear), 1e-12)
        assertEquals(0.2126, StyleSceneSample.linearLuma(255, 0, 0, linear), 1e-12)
        assertEquals(0.0722, StyleSceneSample.linearLuma(0, 0, 255, linear), 1e-12)
    }

    @Test fun halfFloatBitsMatchIEEEBinary16RoundToNearestEven() {
        assertEquals(0x0000, StyleSceneSample.halfFloatBits(0f))
        assertEquals(0x3800, StyleSceneSample.halfFloatBits(0.5f))
        assertEquals(0x3c00, StyleSceneSample.halfFloatBits(1f))
        assertEquals(0x4000, StyleSceneSample.halfFloatBits(2f))
        assertEquals(0xb800, StyleSceneSample.halfFloatBits(-0.5f))
        // 0.3115234375 = 1.24609375 x 2^-2, mantissa 252 exactly representable.
        assertEquals(0x34fc, StyleSceneSample.halfFloatBits(0.3115234375f))
        // 1 + 2^-11 ties between 1.0 (even) and the next step: round stays even.
        assertEquals(0x3c00, StyleSceneSample.halfFloatBits(1.00048828125f))
        // 1 + 3x2^-11 ties between 0x3c01 (odd) and 0x3c02 (even): rounds up.
        assertEquals(0x3c02, StyleSceneSample.halfFloatBits(1.00146484375f))
        assertEquals(0x7c00, StyleSceneSample.halfFloatBits(Float.MAX_VALUE))
        assertEquals(0x7c00, StyleSceneSample.halfFloatBits(Float.POSITIVE_INFINITY))
    }

    @Test fun fittedLightMapAppliesTheCalibratedFitsFloorAndLittleEndianFP16() {
        val map = StyleSceneSample.fittedLightMap(
            doubleArrayOf(0.0, 0.5),
            StyleSceneSample.C_SLOPE, StyleSceneSample.C_INTERCEPT)
        assertEquals(4, map.size)
        fun half(at: Int) = ((map[at + 1].toInt() and 255) shl 8) or (map[at].toInt() and 255)
        assertEquals(StyleSceneSample.halfFloatBits(StyleSceneSample.LIGHT_MAP_FLOOR.toFloat()), half(0))
        assertEquals(StyleSceneSample.halfFloatBits((0.7774 * 0.5 + 0.0294).toFloat()), half(2))
        // The caller hands in the reversed stored grid; the bright tail of a dark-first
        // grid lands at the front of the map.
        val bright = StyleSceneSample.fittedLightMap(doubleArrayOf(1.0, 0.0),
            StyleSceneSample.C_SLOPE, StyleSceneSample.C_INTERCEPT)
        fun firstBits(bytes: ByteArray) = ((bytes[1].toInt() and 255) shl 8) or (bytes[0].toInt() and 255)
        assertTrue(firstBits(bright) > firstBits(map))
    }
}
