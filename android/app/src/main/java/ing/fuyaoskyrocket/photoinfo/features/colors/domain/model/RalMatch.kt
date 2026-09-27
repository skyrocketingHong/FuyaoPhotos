package ing.fuyaoskyrocket.photoinfo.features.colors.domain.model

import java.util.Locale

/**
 * A nearest RAL match for a sampled color.
 */
data class RalMatch(
    val code: Int,
    val name: String,
    val red: Int,
    val green: Int,
    val blue: Int,
) {
    val hex: String
        get() = String.format(Locale.US, "#%02X%02X%02X", red, green, blue)
    val rgbComponents: String
        get() = String.format(Locale.US, "%d, %d, %d", red, green, blue)
}
