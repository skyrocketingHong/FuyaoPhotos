package ing.fuyaoskyrocket.photoinfo.features.colors.domain.model

import android.graphics.Color as AndroidColor
import java.util.Locale

/**
 * A color value expressed in 0–255 integer RGB, with a flag indicating whether
 * any source component was outside the [0, 1] range and had to be clamped.
 */
internal data class ColorValue(
    val red: Int,
    val green: Int,
    val blue: Int,
    val wasClamped: Boolean,
    val redComponent: Double = red / 255.0,
    val greenComponent: Double = green / 255.0,
    val blueComponent: Double = blue / 255.0,
) {
    val argb: Int
        get() = AndroidColor.rgb(red, green, blue)
    val hex: String
        get() = String.format(Locale.US, "#%02X%02X%02X", red, green, blue)
    val rgbComponents: String
        get() = String.format(Locale.US, "%d, %d, %d", red, green, blue)

    fun cssColorFunction(spaceName: String): String = String.format(
        Locale.US,
        "color(%s %s %s %s)",
        spaceName,
        formatColorNumber(redComponent),
        formatColorNumber(greenComponent),
        formatColorNumber(blueComponent),
    )
}

/**
 * The result of sampling a single pixel, carrying independent color-space
 * renderings plus the nearest RAL reference match.
 */
internal data class SampledColor(
    val sourceColorSpaceName: String,
    val sRgb: ColorValue,
    val displayP3: ColorValue,
    val bt2020: ColorValue,
    val adobeRgb: ColorValue,
    val representations: ColorRepresentations,
    val sourceX: Int,
    val sourceY: Int,
    val match: RalMatch,
)

internal fun formatColorNumber(value: Double, decimals: Int = 5): String {
    val threshold = 0.5 / Math.pow(10.0, decimals.toDouble())
    val normalized = if (kotlin.math.abs(value) < threshold) 0.0 else value
    return String.format(Locale.US, "%.${decimals}f", normalized)
        .trimEnd('0')
        .trimEnd('.')
}

/** Gainmap (Ultra HDR) metadata extracted from the bitmap. */
internal data class GainmapDetails(
    val ratioMin: String,
    val ratioMax: String,
    val gamma: String,
    val hdrTransitionRatio: String,
    val fullHdrRatio: String,
)

/** Photograph-level color information shown in the parameter bottom sheet. */
internal data class PhotoColorInfo(
    val width: Int,
    val height: Int,
    val bitmapConfig: String,
    val colorSpaceName: String,
    val colorModel: String,
    val isWideGamut: Boolean,
    val isSrgb: Boolean,
    val componentRange: String,
    val whitePoint: String?,
    val primaries: String?,
    val hasGainmap: Boolean,
    val gainmap: GainmapDetails?,
    val hasHdrContent: Boolean = hasGainmap,
)
