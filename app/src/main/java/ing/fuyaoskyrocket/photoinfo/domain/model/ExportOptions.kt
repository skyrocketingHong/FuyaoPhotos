package ing.fuyaoskyrocket.photoinfo.domain.model

/** Persistent defaults are copied into a fresh, temporary draft for each save. */
data class ExportOptions(
    val format: ExportFormat = ExportFormat.JPEG,
    val jpegQuality: Int = 100,
    val keepExif: Boolean = true,
    val keepLocation: Boolean = false,
    val keepCaptureTime: Boolean = true,
) {
    fun sanitized(jpegRequired: Boolean = false) = copy(
        format = if (jpegRequired) ExportFormat.JPEG else format,
        jpegQuality = jpegQuality.coerceIn(0, 100),
    )
    fun fields() = listOf(format.name, jpegQuality.toString(), keepExif.toString(), keepLocation.toString(), keepCaptureTime.toString())
    companion object {
        fun restore(fields: List<String>) = runCatching {
            require(fields.size == 5)
            ExportOptions(ExportFormat.valueOf(fields[0]), fields[1].toInt(), fields[2].toBooleanStrict(),
                fields[3].toBooleanStrict(), fields[4].toBooleanStrict()).sanitized()
        }.getOrDefault(ExportOptions())
    }
}
