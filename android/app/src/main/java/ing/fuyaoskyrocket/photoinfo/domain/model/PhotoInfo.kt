package ing.fuyaoskyrocket.photoinfo.domain.model

import java.util.Locale

enum class FieldId(val label: String, val accent: Boolean) {
    DEVICE("", true), AUTHOR("SHOT BY", true), LOCATION("LOCATION", true),
    CAMERA("CAMERA", false), IMAGE_SIZE("IMAGE SIZE", false),
    FOCAL_LENGTH("FOCAL LENGTH", false), EXPOSURE("EXPOSURE TIME", false),
    APERTURE("APERTURE", false), ISO("ISO", false),
}

data class PhotoInfo(val values: Map<FieldId, String> = emptyMap()) {
    operator fun get(field: FieldId): String = values[field].orEmpty()
    fun with(field: FieldId, value: String) = copy(values = values + (field to value))
    fun displayRows(): List<InfoRow> = FieldId.entries.mapNotNull { field ->
        val raw = this[field].trim()
        if (raw.isEmpty()) null else {
            // Preserve the deliberate iPHONE wordmark, without mislabelling other cameras.
            val value = if (field == FieldId.DEVICE && raw.startsWith("iphone", true)) {
                "iPHONE" + raw.substring(6).uppercase(Locale.ROOT)
            } else raw.uppercase(Locale.ROOT)
            InfoRow(field, if (field.label.isEmpty()) value else "${field.label}: $value", field.accent)
        }
    }
}

data class InfoRow(val field: FieldId, val text: String, val accent: Boolean)

data class CardStyle(
    val scale: Float = 1f,
    val opacity: Float = .60f,
    val blur: Float = 25f,
    val rightInset: Float = 77f,
    val bottomInset: Float = 35f,
    val cornerRadius: Float = 20f,
    val textScale: Float = 1f,
) {
    fun sanitized() = copy(
        scale = finite(scale, 1f).coerceIn(.6f, 2f),
        opacity = finite(opacity, .6f).coerceIn(0f, 1f),
        blur = finite(blur, 25f).coerceIn(0f, 50f),
        rightInset = finite(rightInset, 77f).coerceIn(0f, 250f),
        bottomInset = finite(bottomInset, 35f).coerceIn(0f, 250f),
        cornerRadius = finite(cornerRadius, 20f).coerceIn(0f, 40f),
        textScale = finite(textScale, 1f).coerceIn(.8f, 1.8f),
    )
    private fun finite(value: Float, fallback: Float) = if (value.isFinite()) value else fallback
}

enum class ExportFormat(val extension: String, val mime: String) {
    JPEG("jpg", "image/jpeg"), PNG("png", "image/png"), HEIC("heic", "image/heic"), AVIF("avif", "image/avif")
}
