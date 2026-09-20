package ing.fuyaoskyrocket.photoinfo.domain.model

data class EditorSettings(
    val defaultAuthor: String = "",
    val resolvePhotoLocation: Boolean = true,
    val fallbackMainFocal: String = "",
) {
    val mainFocalMm: Double? get() = fallbackMainFocal.toDoubleOrNull()?.takeIf { it.isFinite() && it in 1.0..200.0 }
    val validFocal: Boolean get() = fallbackMainFocal.isBlank() || mainFocalMm != null
    fun authorFor(exifAuthor: String): String = exifAuthor.ifBlank { defaultAuthor.trim() }
}
