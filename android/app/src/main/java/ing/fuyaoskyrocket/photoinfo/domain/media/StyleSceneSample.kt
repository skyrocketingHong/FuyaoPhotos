package ing.fuyaoskyrocket.photoinfo.domain.media

/**
 * Scene fields measured from the photo being styled, following the phone-validated
 * calibration of nathanatgit/Shalielie (MIT): styles key '6' holds percentiles of the
 * photo's linearized display luma (LinearImage is the same signal scaled by
 * [LINEAR_IMAGE_SCALE]) and the 32x32 little-endian FP16 c/d light maps, stored rotated
 * 180 degrees from the photo's stored orientation and floored at [LIGHT_MAP_FLOOR].
 */
internal class StyleSceneSample(
    val blackPoint: Double,
    val p02: Double,
    val p10: Double,
    val p25: Double,
    val p50: Double,
    val p75: Double,
    val p98: Double,
    val whitePoint: Double,
    val lightMapC: ByteArray,
    val lightMapD: ByteArray,
) {
    companion object {
        const val LINEAR_IMAGE_SCALE = 0.166
        const val LIGHT_MAP_FLOOR = 0.040741
        const val C_SLOPE = 0.7774
        const val C_INTERCEPT = 0.0294
        const val D_SLOPE = 0.6542
        const val D_INTERCEPT = -0.0128

        // highKey is not scene-derived; the reference values travel with every style file.
        const val TONE_MAPPED_HIGH_KEY = 0.5505164861679077
        const val LINEAR_HIGH_KEY = 0.9925689101216177

        fun srgbToLinear(value: Double): Double =
            if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)

        fun srgbLinearTable(): DoubleArray = DoubleArray(256) { srgbToLinear(it / 255.0) }

        /** Percentile by linear interpolation on a pre-sorted array. */
        fun percentile(sorted: DoubleArray, q: Double): Double {
            if (sorted.isEmpty()) return 0.0
            val position = q * (sorted.size - 1)
            val low = kotlin.math.floor(position).toInt()
            val high = minOf(low + 1, sorted.size - 1)
            val fraction = position - low
            return sorted[low] * (1 - fraction) + sorted[high] * fraction
        }

        /** Rec.709 luma in linear light from 8-bit sRGB-encoded samples. */
        fun linearLuma(red: Int, green: Int, blue: Int, linear: DoubleArray): Double =
            0.2126 * linear[red] + 0.7152 * linear[green] + 0.0722 * linear[blue]

        fun fittedLightMap(storedLinearLumaReversed: DoubleArray, slope: Double, intercept: Double): ByteArray {
            val out = ByteArray(storedLinearLumaReversed.size * 2)
            for (index in storedLinearLumaReversed.indices) {
                val clamped = (slope * storedLinearLumaReversed[index] + intercept).coerceIn(LIGHT_MAP_FLOOR, 1.0)
                val bits = halfFloatBits(clamped.toFloat())
                out[index * 2] = bits.toByte()
                out[index * 2 + 1] = (bits ushr 8).toByte()
            }
            return out
        }

        /** IEEE 754 binary16 with round-to-nearest-even, matching Swift's Float16. */
        fun halfFloatBits(value: Float): Int {
            val bits = java.lang.Float.floatToIntBits(value)
            val sign = (bits ushr 16) and 0x8000
            val exponent32 = (bits ushr 23) and 0xff
            val mantissa = bits and 0x007fffff
            if (exponent32 == 0xff) return sign or 0x7c00 or (if (mantissa != 0) 0x0200 else 0)
            val exponent16 = exponent32 - 127 + 15
            if (exponent16 >= 0x1f) return sign or 0x7c00
            if (exponent16 <= 0) {
                if (exponent16 < -10) return sign
                val m = mantissa or 0x00800000
                val shift = 14 - exponent16
                var half = m ushr shift
                val roundBit = (m shr (shift - 1)) and 1
                val sticky = (m and ((1 shl (shift - 1)) - 1)) != 0
                if (roundBit == 1 && (sticky || (half and 1) == 1)) half++
                return sign or half
            }
            var half = (exponent16 shl 10) or (mantissa ushr 13)
            val roundBit = (mantissa ushr 12) and 1
            val sticky = (mantissa and 0xfff) != 0
            if (roundBit == 1 && (sticky || (half and 1) == 1)) half++
            return sign or half
        }
    }
}
