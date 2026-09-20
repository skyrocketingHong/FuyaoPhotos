package ing.fuyaoskyrocket.photoinfo.domain.session

import ing.fuyaoskyrocket.photoinfo.domain.model.*

/** Only overrides are saved; large original EXIF strings are re-read from the private source. */
data class PhotoEditSnapshot(
    val path: String,
    val overrides: Map<FieldId, String>,
    val style: CardStyle,
    val resolvedLocation: String = "",
    val locationEdited: Boolean = false,
) {
    fun info(base: PhotoInfo) = PhotoInfo(base.values + overrides)
    fun fields(): List<String> = listOf(path) + FieldId.entries.map { field ->
        overrides[field]?.let { "1$it" } ?: "0"
    } + with(style.sanitized()) {
        listOf(scale, opacity, blur, rightInset, bottomInset, cornerRadius, textScale).map { it.toString() }
    } + listOf(resolvedLocation, locationEdited.toString())

    companion object {
        const val MAX_PHOTOS = 50
        val FIELD_COUNT = 1 + FieldId.entries.size + 7 + 2
        fun capture(path: String, base: PhotoInfo, edited: PhotoInfo, style: CardStyle,
            resolvedLocation: String, locationEdited: Boolean) = PhotoEditSnapshot(path,
            FieldId.entries.filter { base[it] != edited[it] }.associateWith { edited[it] },
            style.sanitized(), resolvedLocation, locationEdited)

        fun restore(fields: List<String>): PhotoEditSnapshot {
            require(fields.size == FIELD_COUNT && fields[0].isNotBlank())
            val overrides = FieldId.entries.mapIndexedNotNull { i, field ->
                val value = fields[i + 1]
                require(value == "0" || value.startsWith("1"))
                if (value == "0") null else field to value.substring(1)
            }.toMap()
            val s = fields.drop(1 + FieldId.entries.size).take(7).map { it.toFloat() }
            return PhotoEditSnapshot(fields[0], overrides,
                CardStyle(s[0], s[1], s[2], s[3], s[4], s[5], s[6]).sanitized(),
                fields[FIELD_COUNT - 2], fields.last().toBooleanStrict())
        }
    }
}
