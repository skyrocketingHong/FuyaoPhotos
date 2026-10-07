package ing.fuyaoskyrocket.photoinfo.features.colors.domain.color

import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.ColorValue
import kotlin.math.roundToInt

/** Only the integer representation is clipped; conversions keep the floating-point components. */
internal fun colorValueFromComponents(red: Double, green: Double, blue: Double): ColorValue {
    require(red.isFinite() && green.isFinite() && blue.isFinite())
    return ColorValue(
        red = (red.coerceIn(0.0, 1.0) * 255.0).roundToInt(),
        green = (green.coerceIn(0.0, 1.0) * 255.0).roundToInt(),
        blue = (blue.coerceIn(0.0, 1.0) * 255.0).roundToInt(),
        wasClamped = listOf(red, green, blue).any { it !in -0.00001..1.00001 },
        redComponent = red,
        greenComponent = green,
        blueComponent = blue,
    )
}
