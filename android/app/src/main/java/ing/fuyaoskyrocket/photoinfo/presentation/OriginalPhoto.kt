package ing.fuyaoskyrocket.photoinfo.presentation

import android.graphics.Bitmap
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoDetails
import ing.fuyaoskyrocket.photoinfo.platform.MotionClipSource
import java.io.File

data class OriginalPhoto(
    val id: String,
    val file: File,
    val bitmap: Bitmap?,
    val details: PhotoDetails,
    val hdr: Boolean,
    val hasDepth: Boolean,
    val motion: MotionClipSource?,
    val bitDepth: Int = 8,
)

fun EditorState.originalPhoto(motion: MotionClipSource?): OriginalPhoto? {
    val selected = photos.getOrNull(photoIndex) ?: return null
    val details = photoDetails ?: return null
    return OriginalPhoto(selected.id, File(selected.path), original, details, hdrPhoto, portraitDepth, motion, selected.bitDepth)
}
