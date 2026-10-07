package ing.fuyaoskyrocket.photoinfo.features.colors.domain.color

import android.graphics.Bitmap
import android.graphics.ColorSpace
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.ColorValue
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.SampledColor
import kotlin.math.abs
import kotlin.math.pow

/** Samples one source pixel and converts it independently into each displayed color space. */
internal fun samplePixel(bitmap: Bitmap, x: Int, y: Int): SampledColor {
    val sourceColor = readSourcePixel(bitmap, x, y)
    val xyzD50Components = (sourceColor.colorSpace as? ColorSpace.Rgb)?.let { source ->
        ColorSpace.adapt(source, ColorSpace.ILLUMINANT_D50).toXyz(floatArrayOf(sourceColor.red(), sourceColor.green(), sourceColor.blue()))
    } ?: sourceColor.convert(ColorSpace.get(ColorSpace.Named.CIE_XYZ)).components
    val sRgbValue = xyzD50Components.toRgbValue(ColorSpace.Named.SRGB)
    val representations = colorRepresentationsFrom(sRgbValue, xyzD50Components)
    return SampledColor(
        sourceColorSpaceName = sourceColor.colorSpace.name,
        sourceRgb = colorValueFromComponents(sourceColor.red().toDouble(), sourceColor.green().toDouble(), sourceColor.blue().toDouble()),
        sourceCssSpace = when (sourceColor.colorSpace) {
            ColorSpace.get(ColorSpace.Named.SRGB), ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB) -> "srgb"
            ColorSpace.get(ColorSpace.Named.LINEAR_SRGB), ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB) -> "srgb-linear"
            ColorSpace.get(ColorSpace.Named.DISPLAY_P3) -> "display-p3"
            else -> null
        },
        sRgb = sRgbValue,
        displayP3 = xyzD50Components.toRgbValue(ColorSpace.Named.DISPLAY_P3),
        bt2020 = representations.cssRec2020.let { colorValueFromComponents(it.red, it.green, it.blue) },
        adobeRgb = representations.cssA98Rgb.let { colorValueFromComponents(it.red, it.green, it.blue) },
        representations = representations,
        sourceX = x,
        sourceY = y,
        match = RalCatalog.nearestTo(sRgbValue.argb),
    )
}

private fun FloatArray.toRgbValue(destination: ColorSpace.Named): ColorValue {
    val space = ColorSpace.adapt(ColorSpace.get(destination), ColorSpace.ILLUMINANT_D50) as ColorSpace.Rgb
    val matrix = space.inverseTransform
    val transfer = requireNotNull(space.transferParameters)
    // Color.convert clamps at the destination gamut. Use its profile matrix and
    // transfer parameters directly so CSS coordinates retain out-of-gamut values.
    fun component(row: Int): Float {
        val linear = matrix[row] * this[0] + matrix[row + 3] * this[1] + matrix[row + 6] * this[2]
        val magnitude = abs(linear.toDouble())
        val encoded = if (magnitude >= transfer.c * transfer.d + transfer.f)
            ((magnitude - transfer.e).pow(1.0 / transfer.g) - transfer.b) / transfer.a
        else (magnitude - transfer.f) / transfer.c
        return ((if (linear < 0) -1 else 1) * encoded).toFloat()
    }
    return colorValueFromComponents(component(0).toDouble(), component(1).toDouble(), component(2).toDouble())
}
