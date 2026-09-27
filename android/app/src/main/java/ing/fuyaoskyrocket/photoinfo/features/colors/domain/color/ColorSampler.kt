package ing.fuyaoskyrocket.photoinfo.features.colors.domain.color

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.ColorSpace
import android.os.Build
import androidx.core.graphics.get
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.ColorValue
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.SampledColor
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.pow

/** Samples one source pixel and converts it independently into each displayed color space. */
internal fun samplePixel(bitmap: Bitmap, x: Int, y: Int): SampledColor {
    val sourceColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        bitmap.getColor(x, y)
    } else {
        AndroidColor.valueOf(bitmap[x, y])
    }
    val xyzD50Components = (sourceColor.colorSpace as? ColorSpace.Rgb)?.let { source ->
        ColorSpace.adapt(source, ColorSpace.ILLUMINANT_D50).toXyz(floatArrayOf(sourceColor.red(), sourceColor.green(), sourceColor.blue()))
    } ?: sourceColor.convert(ColorSpace.get(ColorSpace.Named.CIE_XYZ)).components
    val sRgbValue = xyzD50Components.toRgbValue(ColorSpace.Named.SRGB)
    return SampledColor(
        sourceColorSpaceName = bitmap.colorSpace?.name.orEmpty(),
        sRgb = sRgbValue,
        displayP3 = xyzD50Components.toRgbValue(ColorSpace.Named.DISPLAY_P3),
        bt2020 = xyzD50Components.toRgbValue(ColorSpace.Named.BT2020),
        adobeRgb = xyzD50Components.toRgbValue(ColorSpace.Named.ADOBE_RGB),
        representations = colorRepresentationsFrom(sRgbValue, xyzD50Components),
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
    return colorValueFromComponents(component(0), component(1), component(2))
}

/** Builds a [ColorValue] from float components, clamping to [0, 1] if needed. */
internal fun colorValueFromComponents(red: Float, green: Float, blue: Float): ColorValue {
    require(red.isFinite() && green.isFinite() && blue.isFinite())
    val clamped = red !in -0.00001f..1.00001f || green !in -0.00001f..1.00001f || blue !in -0.00001f..1.00001f
    return ColorValue(
        red = (red.coerceIn(0f, 1f) * 255f).roundToInt(),
        green = (green.coerceIn(0f, 1f) * 255f).roundToInt(),
        blue = (blue.coerceIn(0f, 1f) * 255f).roundToInt(),
        wasClamped = clamped,
        redComponent = red.toDouble(),
        greenComponent = green.toDouble(),
        blueComponent = blue.toDouble(),
    )
}
