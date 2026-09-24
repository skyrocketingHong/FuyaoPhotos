package ing.fuyaoskyrocket.photoinfo.domain.model

import ing.fuyaoskyrocket.photoinfo.domain.metadata.PhotoCoordinates

/** Facts from the selected file. Absent source metadata stays absent. */
data class PhotoDetails(
    val displayName: String? = null,
    val mimeType: String? = null,
    val byteCount: Long? = null,
    val width: Int = 0,
    val height: Int = 0,
    val colorSpace: String? = null,
    val exif: Map<String, String> = emptyMap(),
    val coordinates: PhotoCoordinates? = null,
)
