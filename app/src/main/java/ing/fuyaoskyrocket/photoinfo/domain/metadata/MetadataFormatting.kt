package ing.fuyaoskyrocket.photoinfo.domain.metadata

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.roundToLong

/** Formatting only; absent metadata never becomes invented camera information. */
object MetadataFormatting {
    fun number(value: Double, places: Int = 2): String {
        if (!value.isFinite() || value <= 0) return ""
        val original = BigDecimal.valueOf(value)
        val rounded = original.setScale(places, RoundingMode.HALF_UP)
        // Never label a positive exposure, focal length or pixel count as zero.
        return (if (rounded.signum() == 0) original else rounded).stripTrailingZeros().toPlainString()
    }

    fun device(make: String?, model: String?): String {
        val name = model.orEmpty().trim()
        val brand = make.orEmpty().trim()
        if (name.isEmpty()) return brand
        if (name.startsWith("iphone", true)) return "iPHONE" + name.substring(6)
        return if (brand.isEmpty() || name.startsWith(brand, true)) name else "$brand $name"
    }

    fun megapixels(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return ""
        val mp = width.toLong() * height.toLong() / 1_000_000.0
        return number(mp, if (mp < .1) 3 else 1) + "MP"
    }

    fun exposure(seconds: Double): String {
        if (!seconds.isFinite() || seconds <= 0) return ""
        if (seconds >= 1) return number(seconds, 3) + "SEC"
        val reciprocal = 1.0 / seconds
        if (reciprocal > Long.MAX_VALUE.toDouble()) return number(seconds, 12) + "SEC"
        val denominator = reciprocal.roundToLong().coerceAtLeast(1)
        return if (abs(seconds * denominator - 1) <= .005) "1/$denominator"
            else number(seconds, 6) + "SEC"
    }

    /** Only 35mm-equivalent EXIF is accepted; actual focal length and digital zoom are not equivalent. */
    fun equivalentFocalLength(mm: Double): String = number(mm).takeIf { it.isNotEmpty() }
        ?.let { "$it MM" }.orEmpty()
}
