package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.TenBitYuv
import org.junit.Assert.*
import org.junit.Test

class TenBitYuvTest {
    @Test fun halfFloatDecodingMatchesKnownValues() {
        // 1.0 = 0x3C00, 0.5 = 0x3800, 0.25 = 0x3400, smallest normal 2^-14.
        assertEquals(1f, TenBitYuv.halfToFloat(0x3c00), 0f)
        assertEquals(0.5f, TenBitYuv.halfToFloat(0x3800), 0f)
        assertEquals(0.25f, TenBitYuv.halfToFloat(0x3400), 0f)
        assertEquals(6.1035156e-5f, TenBitYuv.halfToFloat(0x0400), 1e-12f)
        assertEquals(1.5f, TenBitYuv.halfToFloat(0x3e00), 0f)
        assertEquals(-2f, TenBitYuv.halfToFloat(0xc000), 0f)
    }

    @Test fun srgbOetfHitsTheStandardEndpoints() {
        assertEquals(0f, TenBitYuv.srgbOetf(0f), 0f)
        assertEquals(1f, TenBitYuv.srgbOetf(1f), 1e-6f)
        assertEquals(0.7354f, TenBitYuv.srgbOetf(0.5f), 1e-3f)
        assertEquals(0.04045f, TenBitYuv.srgbOetf(0.0031308f), 1e-4f)
    }

    @Test fun pqOetfIsMonotonicAndBounded() {
        assertEquals(0f, TenBitYuv.pqOetf(0f), 0f)
        var previous = -1f
        var value = 0f
        while (value <= 2f) {
            val encoded = TenBitYuv.pqOetf(value)
            assertTrue(encoded in 0f..1f)
            assertTrue(encoded > previous - 1e-6f)
            previous = encoded
            value += 0.05f
        }
        // Reference white (linear 1.0 on the Android F16 scale) sits clearly above mid gray.
        assertTrue(TenBitYuv.pqOetf(1f) > 0.5f)
    }

    @Test fun grayPixelsEncodeWithNeutralChroma() {
        val gray = TenBitYuv.toYuv2020(0.18f, 0.18f, 0.18f,
            floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), TenBitYuv.Transfer.SRGB)
        assertEquals(0f, gray[1], 1e-4f)
        assertEquals(0f, gray[2], 1e-4f)
        // Mid gray lands near mid code value after quantization.
        val quantized = TenBitYuv.quantize10(gray[0]).toInt() and 0xffff shr 6
        // Scene-linear 18 percent gray lands near mid gray after the sRGB curve.
        assertTrue(quantized in 420..520)
    }

    @Test fun primariesTransformKeepsWhiteWhite() {
        for (matrix in listOf(
            floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            floatArrayOf(0.6274040f, 0.3292860f, 0.0433131f, 0.0690970f, 0.9195400f, 0.0113612f,
                0.0163916f, 0.0880132f, 0.8955953f))) {
            val yuv = TenBitYuv.toYuv2020(1f, 1f, 1f, matrix, TenBitYuv.Transfer.SRGB)
            assertEquals(1f, yuv[0], 1e-3f)
            assertEquals(0.5f, yuv[1] + 0.5f, 1e-3f)
            assertEquals(0.5f, yuv[2] + 0.5f, 1e-3f)
        }
    }

    @Test fun quantizationPacksTenBitsIntoTheHighBits() {
        assertEquals(0, TenBitYuv.quantize10(0f).toInt())
        assertEquals(1023, TenBitYuv.quantize10(1f).toInt() and 0xffff shr 6)
        assertEquals(1023 shl 6, TenBitYuv.quantize10(1.5f).toInt() and 0xffff)
        // Rounding lands on the nearest ten-bit step.
        val near = TenBitYuv.quantize10(512.5f / 1023f).toInt() and 0xffff shr 6
        assertTrue(near in 511..513)
    }

    @Test fun p010LayoutPlacesLumaThenInterleavedChroma() {
        // 2x2 pixels: distinct gray levels per row so subsampling is visible.
        fun half(value: Float): Int {
            val bits = java.lang.Float.floatToRawIntBits(value)
            val exponent = ((bits shr 23) and 0xff) - 127 + 15
            val fraction = (bits shr 13) and 0x3ff
            return (exponent shl 10) or fraction
        }
        val halfs = ShortArray(2 * 2 * 4)
        val values = floatArrayOf(0.1f, 0.1f, 0.1f, 1f, 0.4f, 0.4f, 0.4f, 1f,
            0.8f, 0.8f, 0.8f, 1f, 0.6f, 0.6f, 0.6f, 1f)
        values.forEachIndexed { index, value -> halfs[index] = half(value).toShort() }
        val bytes = TenBitYuv.encodeP010(java.nio.ShortBuffer.wrap(halfs), 2, 2, stride = 4, sliceHeight = 2, colorSpaceName = "LINEAR_sRGB")
        // Stride 4 shorts -> 8 bytes per row; two luma rows, then one chroma row.
        assertEquals((4 * 2 + 4) * 2, bytes.size)
        fun sample(at: Int) = ((bytes[at + 1].toInt() and 0xff) shl 8 or (bytes[at].toInt() and 0xff))
        fun luma(row: Int, column: Int) = sample((row * 8 + column * 2)) and 0xffff shr 6
        assertTrue(luma(0, 0) < luma(0, 1))
        assertTrue(luma(1, 0) > luma(0, 0))
        // Chroma row starts right after the luma plane.
        val chromaAt = 8 * 2
        assertTrue(sample(chromaAt) != 0)
    }

    @Test fun colorSpaceClassificationFallsBackSafely() {
        assertEquals(TenBitYuv.Primaries.BT2020, TenBitYuv.primariesFor("LINEAR_BT2020"))
        assertEquals(TenBitYuv.Primaries.P3, TenBitYuv.primariesFor("Display P3"))
        assertEquals(TenBitYuv.Primaries.BT709, TenBitYuv.primariesFor("sRGB"))
        assertEquals(TenBitYuv.Transfer.PQ, TenBitYuv.transferFor("BT2020_PQ"))
        assertEquals(TenBitYuv.Transfer.PQ, TenBitYuv.transferFor("BT2020_HLG"))
        assertEquals(TenBitYuv.Transfer.SRGB, TenBitYuv.transferFor("LINEAR_sRGB"))
    }
}
