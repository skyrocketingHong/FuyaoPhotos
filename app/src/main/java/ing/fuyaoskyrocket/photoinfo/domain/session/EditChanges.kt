package ing.fuyaoskyrocket.photoinfo.domain.session

import ing.fuyaoskyrocket.photoinfo.domain.model.FieldId
import java.security.MessageDigest

object EditChanges {
    fun form(initial: List<String>, current: List<String>, numeric: Set<Int> = emptySet()): Boolean {
        fun value(text: String, index: Int): String {
            val trimmed = text.trim()
            return if (index in numeric) trimmed.toDoubleOrNull()?.takeIf { it.isFinite() }?.toString() ?: trimmed else trimmed
        }
        return initial.size != current.size || initial.indices.any { value(initial[it], it) != value(current[it], it) }
    }

    /** Only user edits count: automatic GPS lookups do not create a discard warning. */
    fun fingerprint(edit: PhotoEditSnapshot, quality: Int, keepMetadata: Boolean, font: String, keepLocation: Boolean = false, keepCaptureTime: Boolean = true): String {
        val location = edit.overrides[FieldId.LOCATION].orEmpty()
        val manualLocation = if (edit.locationEdited && location != edit.resolvedLocation) mapOf(FieldId.LOCATION to location) else emptyMap()
        val normalized = edit.copy(path = "", resolvedLocation = "", locationEdited = false,
            overrides = (edit.overrides - FieldId.LOCATION) + manualLocation)
        val fields = normalized.fields() + listOf(quality.toString(), keepMetadata.toString(), font) +
            (if (keepLocation || !keepCaptureTime) listOf("metadata-v2", keepLocation.toString(), keepCaptureTime.toString()) else emptyList())
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach { value ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
