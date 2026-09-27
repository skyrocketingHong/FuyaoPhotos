package ing.fuyaoskyrocket.photoinfo.features.colors.domain.color

import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.CmykColorValue
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.ColorRepresentations
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.ColorValue
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.HslColorValue
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.LabColorValue
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.LchColorValue
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.OklabColorValue
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.OklchColorValue
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.PredefinedRgbColorValue
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.XyzColorValue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** W3C CSS Color 4 conversions, starting from Android's D50 XYZ profile connection space. */
internal fun colorRepresentationsFrom(
    sRgb: ColorValue,
    xyzD50Components: FloatArray,
): ColorRepresentations {
    val xyzD50 = XyzColorValue(
        x = xyzD50Components[0].toDouble(),
        y = xyzD50Components[1].toDouble(),
        z = xyzD50Components[2].toDouble(),
    )
    val xyzD65 = xyzD50.toD65()
    val cieLab = xyzD50.toLab()
    val okLab = xyzD65.toOklab()
    return ColorRepresentations(
        hsl = sRgb.toHsl(),
        cmyk = sRgb.toCmyk(),
        cieLab = cieLab,
        cieLch = cieLab.toLch(),
        okLab = okLab,
        okLch = okLab.toOklch(),
        xyzD50 = xyzD50,
        xyzD65 = xyzD65,
        cssA98Rgb = xyzD65.toCssA98Rgb(),
        cssRec2020 = xyzD65.toCssRec2020(),
        cssNamedColor = CssNamedColors.nearestTo(sRgb, okLab),
    )
}

private fun ColorValue.toHsl(): HslColorValue {
    val red = redComponent.coerceIn(0.0, 1.0)
    val green = greenComponent.coerceIn(0.0, 1.0)
    val blue = blueComponent.coerceIn(0.0, 1.0)
    val maximum = max(red, max(green, blue))
    val minimum = min(red, min(green, blue))
    val delta = maximum - minimum
    val lightness = (maximum + minimum) / 2.0
    if (delta == 0.0) {
        return HslColorValue(hue = 0.0, saturation = 0.0, lightness = lightness)
    }

    val rawHue = when (maximum) {
        red -> 60.0 * (((green - blue) / delta) % 6.0)
        green -> 60.0 * (((blue - red) / delta) + 2.0)
        else -> 60.0 * (((red - green) / delta) + 4.0)
    }
    return HslColorValue(
        hue = (rawHue + 360.0) % 360.0,
        saturation = delta / (1.0 - abs(2.0 * lightness - 1.0)),
        lightness = lightness,
    )
}

private fun ColorValue.toCmyk(): CmykColorValue {
    val red = redComponent.coerceIn(0.0, 1.0)
    val green = greenComponent.coerceIn(0.0, 1.0)
    val blue = blueComponent.coerceIn(0.0, 1.0)
    val black = 1.0 - max(red, max(green, blue))
    if (black >= 1.0) {
        return CmykColorValue(cyan = 0.0, magenta = 0.0, yellow = 0.0, black = 1.0)
    }
    val scale = 1.0 - black
    return CmykColorValue(
        cyan = (1.0 - red - black) / scale,
        magenta = (1.0 - green - black) / scale,
        yellow = (1.0 - blue - black) / scale,
        black = black,
    )
}

private fun XyzColorValue.toD65(): XyzColorValue = XyzColorValue(
    x = 0.955473421488075 * x - 0.02309845494876471 * y + 0.06325924320057072 * z,
    y = -0.0283697093338637 * x + 1.0099953980813041 * y + 0.021041441191917323 * z,
    z = 0.012314014864481998 * x - 0.020507649298898964 * y + 1.330365926242124 * z,
)

private fun XyzColorValue.toLab(): LabColorValue {
    val epsilon = 216.0 / 24389.0
    val kappa = 24389.0 / 27.0
    val d50X = 0.3457 / 0.3585
    val d50Z = (1.0 - 0.3457 - 0.3585) / 0.3585
    fun labCurve(value: Double): Double = if (value > epsilon) {
        Math.cbrt(value)
    } else {
        (kappa * value + 16.0) / 116.0
    }

    val fx = labCurve(x / d50X)
    val fy = labCurve(y)
    val fz = labCurve(z / d50Z)
    return LabColorValue(
        lightness = 116.0 * fy - 16.0,
        a = 500.0 * (fx - fy),
        b = 200.0 * (fy - fz),
    )
}

private fun LabColorValue.toLch(): LchColorValue {
    val chroma = sqrt(a * a + b * b)
    return LchColorValue(
        lightness = lightness,
        chroma = chroma,
        hue = polarHue(a, b, chroma, neutralThreshold = 0.0015),
    )
}

private fun XyzColorValue.toOklab(): OklabColorValue {
    val l = 0.8190224379967030 * x + 0.3619062600528904 * y - 0.1288737815209879 * z
    val m = 0.0329836539323885 * x + 0.9292868615863434 * y + 0.0361446663506424 * z
    val s = 0.0481771893596242 * x + 0.2642395317527308 * y + 0.6335478284694309 * z
    val lRoot = Math.cbrt(l)
    val mRoot = Math.cbrt(m)
    val sRoot = Math.cbrt(s)
    return OklabColorValue(
        lightness = 0.2104542683093140 * lRoot +
            0.7936177747023054 * mRoot -
            0.0040720430116193 * sRoot,
        a = 1.9779985324311684 * lRoot -
            2.4285922420485799 * mRoot +
            0.4505937096174110 * sRoot,
        b = 0.0259040424655478 * lRoot +
            0.7827717124575296 * mRoot -
            0.8086757549230774 * sRoot,
    )
}

private fun XyzColorValue.toCssA98Rgb(): PredefinedRgbColorValue {
    val linearRed = 1829569.0 / 896150.0 * x -
        506331.0 / 896150.0 * y -
        308931.0 / 896150.0 * z
    val linearGreen = -851781.0 / 878810.0 * x +
        1648619.0 / 878810.0 * y +
        36519.0 / 878810.0 * z
    val linearBlue = 16779.0 / 1248040.0 * x -
        147721.0 / 1248040.0 * y +
        1266979.0 / 1248040.0 * z
    return PredefinedRgbColorValue(
        red = encodePower(linearRed, exponent = 256.0 / 563.0),
        green = encodePower(linearGreen, exponent = 256.0 / 563.0),
        blue = encodePower(linearBlue, exponent = 256.0 / 563.0),
    )
}

private fun XyzColorValue.toCssRec2020(): PredefinedRgbColorValue {
    val linearRed = 30757411.0 / 17917100.0 * x -
        6372589.0 / 17917100.0 * y -
        4539589.0 / 17917100.0 * z
    val linearGreen = -19765991.0 / 29648200.0 * x +
        47925759.0 / 29648200.0 * y +
        467509.0 / 29648200.0 * z
    val linearBlue = 792561.0 / 44930125.0 * x -
        1921689.0 / 44930125.0 * y +
        42328811.0 / 44930125.0 * z
    return PredefinedRgbColorValue(
        red = encodePower(linearRed, exponent = 1.0 / 2.4),
        green = encodePower(linearGreen, exponent = 1.0 / 2.4),
        blue = encodePower(linearBlue, exponent = 1.0 / 2.4),
    )
}

private fun encodePower(value: Double, exponent: Double): Double {
    val sign = if (value < 0.0) -1.0 else 1.0
    return sign * Math.pow(abs(value), exponent)
}

private fun OklabColorValue.toOklch(): OklchColorValue {
    val chroma = sqrt(a * a + b * b)
    return OklchColorValue(
        lightness = lightness,
        chroma = chroma,
        hue = polarHue(a, b, chroma, neutralThreshold = 0.000004),
    )
}

private fun polarHue(
    a: Double,
    b: Double,
    chroma: Double,
    neutralThreshold: Double,
): Double {
    if (chroma <= neutralThreshold) return Double.NaN
    val degrees = Math.toDegrees(atan2(b, a))
    return if (degrees < 0.0) degrees + 360.0 else degrees
}

internal fun srgbColorToOklab(color: ColorValue): OklabColorValue {
    val red = linearizeSrgb(color.red / 255.0)
    val green = linearizeSrgb(color.green / 255.0)
    val blue = linearizeSrgb(color.blue / 255.0)
    val xyzD65 = XyzColorValue(
        x = 0.41239079926595934 * red + 0.35758433938387796 * green + 0.1804807884018343 * blue,
        y = 0.21263900587151027 * red + 0.7151686787677559 * green + 0.07219231536073371 * blue,
        z = 0.01933081871559182 * red + 0.11919477979462599 * green + 0.9505321522496607 * blue,
    )
    return xyzD65.toOklab()
}

private fun linearizeSrgb(value: Double): Double = if (value <= 0.04045) {
    value / 12.92
} else {
    Math.pow((value + 0.055) / 1.055, 2.4)
}

internal fun deltaEOk(first: OklabColorValue, second: OklabColorValue): Double {
    val deltaL = first.lightness - second.lightness
    val deltaA = first.a - second.a
    val deltaB = first.b - second.b
    return sqrt(deltaL * deltaL + deltaA * deltaA + deltaB * deltaB)
}
