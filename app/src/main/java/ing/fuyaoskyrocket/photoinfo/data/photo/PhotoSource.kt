package ing.fuyaoskyrocket.photoinfo.data.photo

import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoInfo
import ing.fuyaoskyrocket.photoinfo.domain.metadata.PhotoCoordinates
import java.io.File

data class PhotoSource(
    val file: File,
    val width: Int,
    val height: Int,
    val orientation: Int,
    val info: PhotoInfo,
    val captureTags: Map<String, String>,
    val coordinates: PhotoCoordinates? = null,
    val media: ing.fuyaoskyrocket.photoinfo.domain.media.MediaEnvelope = ing.fuyaoskyrocket.photoinfo.domain.media.MediaEnvelope(true),
)
