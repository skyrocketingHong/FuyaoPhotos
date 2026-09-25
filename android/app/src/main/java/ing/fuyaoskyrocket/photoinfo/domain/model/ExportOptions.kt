package ing.fuyaoskyrocket.photoinfo.domain.model

/** Persistent defaults are copied into a fresh, temporary draft for each save. */
data class ExportOptions(
    val format: ExportFormat = ExportFormat.JPEG,
    val jpegQuality: Int = 100,
    val keepExif: Boolean = true,
    val keepLocation: Boolean = false,
    val keepCaptureTime: Boolean = true,
    val separateLivePhoto: Boolean = false,
    val applePortrait: Boolean = false,
    val appleStyle: Boolean = false,
) {
    fun sanitized(jpegRequired: Boolean = false) = copy(
        format = if (jpegRequired && format == ExportFormat.PNG) ExportFormat.JPEG else format,
        jpegQuality = jpegQuality.coerceIn(0, 100),
    )
    fun fields() = listOf(format.name, jpegQuality.toString(), keepExif.toString(), keepLocation.toString(), keepCaptureTime.toString(), separateLivePhoto.toString(), applePortrait.toString(), appleStyle.toString())
    companion object {
        fun restore(fields: List<String>) = runCatching {
            require(fields.size in 5..8)
            ExportOptions(ExportFormat.valueOf(fields[0]), fields[1].toInt(), fields[2].toBooleanStrict(),
                fields[3].toBooleanStrict(), fields[4].toBooleanStrict(), fields.getOrNull(5)?.toBooleanStrict() ?: false,
                fields.getOrNull(6)?.toBooleanStrict() ?: false,
                fields.getOrNull(7)?.toBooleanStrict() ?: false).sanitized()
        }.getOrDefault(ExportOptions())
    }
}
