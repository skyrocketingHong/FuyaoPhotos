package ing.fuyaoskyrocket.photoinfo.features.colors.domain.model

import java.util.Locale

internal data class HslColorValue(
    val hue: Double,
    val saturation: Double,
    val lightness: Double,
) {
    val componentsText: String
        get() = String.format(
            Locale.US,
            "%.2f°, %.2f%%, %.2f%%",
            hue,
            saturation * 100.0,
            lightness * 100.0,
        )
    val cssText: String
        get() = String.format(
            Locale.US,
            "hsl(%.2f %.2f%% %.2f%%)",
            hue,
            saturation * 100.0,
            lightness * 100.0,
        )
}

internal data class CmykColorValue(
    val cyan: Double,
    val magenta: Double,
    val yellow: Double,
    val black: Double,
) {
    val componentsText: String
        get() = String.format(
            Locale.US,
            "%.2f%%, %.2f%%, %.2f%%, %.2f%%",
            cyan * 100.0,
            magenta * 100.0,
            yellow * 100.0,
            black * 100.0,
        )
    val normalizedText: String
        get() = listOf(cyan, magenta, yellow, black)
            .joinToString(", ") { formatColorNumber(it, decimals = 4) }
}

internal data class LabColorValue(
    val lightness: Double,
    val a: Double,
    val b: Double,
) {
    val componentsText: String
        get() = String.format(Locale.US, "%.2f, %.2f, %.2f", lightness, a, b)
    val cssText: String
        get() = String.format(Locale.US, "lab(%.2f%% %.2f %.2f)", lightness, a, b)
}

internal data class LchColorValue(
    val lightness: Double,
    val chroma: Double,
    val hue: Double,
) {
    private val hueText: String
        get() = if (hue.isNaN()) "N/A" else String.format(Locale.US, "%.2f°", hue)
    private val cssHueText: String
        get() = if (hue.isNaN()) "none" else String.format(Locale.US, "%.2f", hue)
    val componentsText: String
        get() = String.format(Locale.US, "%.2f, %.2f, %s", lightness, chroma, hueText)
    val cssText: String
        get() = String.format(Locale.US, "lch(%.2f%% %.2f %s)", lightness, chroma, cssHueText)
}

internal data class OklabColorValue(
    val lightness: Double,
    val a: Double,
    val b: Double,
) {
    val componentsText: String
        get() = String.format(Locale.US, "%.4f, %.4f, %.4f", lightness, a, b)
    val cssText: String
        get() = String.format(
            Locale.US,
            "oklab(%.2f%% %.4f %.4f)",
            lightness * 100.0,
            a,
            b,
        )
}

internal data class OklchColorValue(
    val lightness: Double,
    val chroma: Double,
    val hue: Double,
) {
    private val hueText: String
        get() = if (hue.isNaN()) "N/A" else String.format(Locale.US, "%.2f°", hue)
    private val cssHueText: String
        get() = if (hue.isNaN()) "none" else String.format(Locale.US, "%.2f", hue)
    val componentsText: String
        get() = String.format(Locale.US, "%.4f, %.4f, %s", lightness, chroma, hueText)
    val cssText: String
        get() = String.format(
            Locale.US,
            "oklch(%.2f%% %.4f %s)",
            lightness * 100.0,
            chroma,
            cssHueText,
        )
}

internal data class XyzColorValue(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    val componentsText: String
        get() = listOf(x, y, z).joinToString(", ") { formatColorNumber(it) }

    fun cssText(spaceName: String): String = String.format(
        Locale.US,
        "color(%s %s %s %s)",
        spaceName,
        formatColorNumber(x),
        formatColorNumber(y),
        formatColorNumber(z),
    )
}

internal data class PredefinedRgbColorValue(
    val red: Double,
    val green: Double,
    val blue: Double,
) {
    fun cssText(spaceName: String): String = String.format(
        Locale.US,
        "color(%s %s %s %s)",
        spaceName,
        formatColorNumber(red),
        formatColorNumber(green),
        formatColorNumber(blue),
    )
}

internal data class CssNamedColorMatch(
    val names: List<String>,
    val color: ColorValue,
    val isExact: Boolean,
    val deltaEOk: Double,
)

internal data class ColorRepresentations(
    val hsl: HslColorValue,
    val cmyk: CmykColorValue,
    val cieLab: LabColorValue,
    val cieLch: LchColorValue,
    val okLab: OklabColorValue,
    val okLch: OklchColorValue,
    val xyzD50: XyzColorValue,
    val xyzD65: XyzColorValue,
    val cssA98Rgb: PredefinedRgbColorValue,
    val cssRec2020: PredefinedRgbColorValue,
    val cssNamedColor: CssNamedColorMatch,
)
