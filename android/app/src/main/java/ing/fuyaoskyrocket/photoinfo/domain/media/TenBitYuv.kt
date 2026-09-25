package ing.fuyaoskyrocket.photoinfo.domain.media

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure conversion from a scene-linear RGBA_F16 plane into the P010 layout MediaCodec's
 * HEVC Main10 encoders accept. Everything here is JVM-testable by design; the encoder
 * wrapper owns the Android types.
 */
internal object TenBitYuv {
    // Linear RGB to BT.2020 primaries. Sources arrive in BT.709, Display P3 or BT.2020.
    private val BT709_TO_2020 = floatArrayOf(
        0.6274040f, 0.3292860f, 0.0433131f,
        0.0690970f, 0.9195400f, 0.0113612f,
        0.0163916f, 0.0880132f, 0.8955953f)
    private val P3_TO_2020 = floatArrayOf(
        0.7934976f, 0.1910618f, 0.0154470f,
        0.0226002f, 0.9392470f, 0.0381563f,
        0.0145170f, 0.0527470f, 0.9327390f)
    private val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    // BT.2020 full-range YUV; KR 0.2627, KB 0.0593.
    private const val KR = 0.2627f
    private const val KB = 0.0593f

    // PQ (ST 2084) constants; the Android F16 convention keeps 1.0 near 203-nit reference
    // white, so linear values are lifted onto the PQ absolute scale first.
    private const val PQ_C1 = 0.8359375f
    private const val PQ_C2 = 18.8515625f
    private const val PQ_C3 = 18.6875f
    private const val PQ_M1 = 0.1593017578125f
    private const val PQ_M2 = 78.84375f
    private const val PQ_SIGNAL_MAX = 10000f / 203f

    enum class Primaries { BT709, P3, BT2020 }
    enum class Transfer { SRGB, PQ }

    fun halfToFloat(value: Int): Float {
        val bits = value and 0xffff
        val sign = if (bits and 0x8000 != 0) -1f else 1f
        val exponent = (bits shr 10) and 0x1f
        val fraction = bits and 0x3ff
        return when (exponent) {
            0 -> sign * fraction * 9.765625e-4f
            31 -> if (fraction == 0) sign * Float.POSITIVE_INFINITY else Float.NaN
            else -> {
                val scale = if (exponent >= 15) (1 shl (exponent - 15)).toFloat()
                else 1f / (1 shl (15 - exponent))
                sign * scale * (1f + fraction / 1024f)
            }
        }
    }

    fun primariesFor(colorSpaceName: String): Primaries = when {
        colorSpaceName.contains("2020", true) -> Primaries.BT2020
        colorSpaceName.contains("P3", true) -> Primaries.P3
        else -> Primaries.BT709
    }

    fun transferFor(colorSpaceName: String): Transfer = when {
        colorSpaceName.contains("PQ", true) || colorSpaceName.contains("HLG", true) -> Transfer.PQ
        else -> Transfer.SRGB
    }

    fun srgbOetf(value: Float): Float {
        if (value <= 0f) return 0f
        if (value >= 1f) return 1f
        return if (value <= 0.0031308f) 12.92f * value else 1.055f * value.pow(1f / 2.4f) - 0.055f
    }

    fun pqOetf(value: Float): Float {
        if (value <= 0f) return 0f
        val scaled = min(value * PQ_SIGNAL_MAX, 1f).pow(PQ_M1)
        return ((PQ_C1 + PQ_C2 * scaled) / (1f + PQ_C3 * scaled)).pow(PQ_M2)
    }

    /** Converts one pixel from scene-linear source RGB into full-range BT.2020 YUV. */
    fun toYuv2020(r: Float, g: Float, b: Float, matrix: FloatArray, transfer: Transfer): FloatArray {
        fun clamp(value: Float) = min(max(value, 0f), 1.5f)
        val lr = clamp(r); val lg = clamp(g); val lb = clamp(b)
        val r2020 = clamp(matrix[0] * lr + matrix[1] * lg + matrix[2] * lb)
        val g2020 = clamp(matrix[3] * lr + matrix[4] * lg + matrix[5] * lb)
        val b2020 = clamp(matrix[6] * lr + matrix[7] * lg + matrix[8] * lb)
        val encode = if (transfer == Transfer.PQ) ::pqOetf else ::srgbOetf
        val ry = encode(r2020); val gy = encode(g2020); val by = encode(b2020)
        val y = KR * ry + (1f - KR - KB) * gy + KB * by
        return floatArrayOf(y, by - y, ry - y)
    }

    /**
     * Packs an RGBA_F16 plane into P010: Y plane of sliceHeight rows at stride shorts,
     * then interleaved UV at half height. Returns little-endian bytes.
     */
    fun encodeP010(halfs: java.nio.ShortBuffer, width: Int, height: Int, stride: Int, sliceHeight: Int,
        colorSpaceName: String, hdrTransferAllowed: Boolean = true): ByteArray {
        require(halfs.remaining() >= width * height * 4 && stride >= width && sliceHeight >= height) { "p010 source ${halfs.remaining()} shorts, ${width}x${height} at $stride" }
        val matrix = when (primariesFor(colorSpaceName)) {
            Primaries.BT709 -> BT709_TO_2020
            Primaries.P3 -> P3_TO_2020
            Primaries.BT2020 -> IDENTITY
        }
        // An SDR-container source (Ultra HDR JPEG) must keep its SDR base curve even when the
        // decoded F16 plane carries a wide-gamut name; only true HDR containers go PQ/HLG.
        val transfer = if (hdrTransferAllowed) transferFor(colorSpaceName) else Transfer.SRGB
        val luma = ShortArray(stride * sliceHeight)
        // P010 chroma is horizontally subsampled: width/2 interleaved U-V pairs per row.
        val chroma = ShortArray(stride * (sliceHeight / 2))
        for (row in 0 until height) {
            for (column in 0 until width) {
                val at = (row * width + column) * 4
                val yuv = toYuv2020(
                    halfToFloat(halfs.get(at).toInt()),
                    halfToFloat(halfs.get(at + 1).toInt()),
                    halfToFloat(halfs.get(at + 2).toInt()), matrix, transfer)
                luma[row * stride + column] = quantize10(yuv[0])
            }
        }
        for (uvRow in 0 until (height + 1) / 2) {
            for (uvColumn in 0 until (width + 1) / 2) {
                val at = (uvRow * 2 * width + uvColumn * 2) * 4
                val yuv = toYuv2020(
                    halfToFloat(halfs.get(at).toInt()),
                    halfToFloat(halfs.get(at + 1).toInt()),
                    halfToFloat(halfs.get(at + 2).toInt()), matrix, transfer)
                chroma[uvRow * stride + uvColumn * 2] = quantize10(yuv[1] + 0.5f)
                chroma[uvRow * stride + uvColumn * 2 + 1] = quantize10(yuv[2] + 0.5f)
            }
        }
        val out = ByteArray((luma.size + chroma.size) * 2)
        fun write(source: ShortArray, offset: Int) {
            for (index in source.indices) {
                val value = source[index].toInt()
                out[offset + index * 2] = value.toByte()
                out[offset + index * 2 + 1] = (value shr 8).toByte()
            }
        }
        write(luma, 0)
        write(chroma, luma.size * 2)
        return out
    }

    fun quantize10(value: Float): Short {
        val scaled = (min(max(value, 0f), 1f) * 1023f).roundToInt()
        return (scaled shl 6).toShort()
    }

    fun closeEnough(a: Float, b: Float, epsilon: Float = 1e-3f) = abs(a - b) < epsilon
}
