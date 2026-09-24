package ing.fuyaoskyrocket.photoinfo.presentation

import android.graphics.Bitmap
import android.net.Uri
import ing.fuyaoskyrocket.photoinfo.domain.model.CardStyle
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportFormat
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoInfo
import ing.fuyaoskyrocket.photoinfo.domain.model.EditorSettings

enum class LocationStatus { IDLE, RESOLVING, RESOLVED, UNAVAILABLE, NO_GPS, DISABLED }

data class ExportedPhoto(val uri: Uri, val format: ExportFormat, val movieUri: Uri? = null)
data class EditorNotice(val id: Long, val text: String, val photos: List<ExportedPhoto> = emptyList())
data class PhotoPageItem(val id: String, val width: Int, val height: Int)
data class EditorState(
    val info: PhotoInfo = PhotoInfo(),
    val style: CardStyle = CardStyle(),
    val original: Bitmap? = null,
    val preview: Bitmap? = null,
    val width: Int = 0,
    val height: Int = 0,
    val busy: Boolean = false,
    val exporting: Boolean = false,
    val rendering: Boolean = false,
    val error: String? = null,
    val errorTitle: Int = ing.fuyaoskyrocket.photoinfo.R.string.error_import_title,
    val previewError: String? = null,
    val notice: EditorNotice? = null,
    val fontName: String? = null,
    val hasCustomFont: Boolean = false,
    val keepCaptureMetadata: Boolean = true,
    val keepLocation: Boolean = false,
    val keepCaptureTime: Boolean = true,
    val exported: ExportedPhoto? = null,
    val settings: EditorSettings = EditorSettings(),
    val locationStatus: LocationStatus = LocationStatus.IDLE,
    val hasPhotoGps: Boolean = false,
    val preservationBlocked: Boolean = false,
    val jpegRequired: Boolean = false,
    val motionPhoto: Boolean = false,
    val hdrPhoto: Boolean = false,
    val mediaMessage: Int? = null,
    val sourceDevice: String = "",
    val sourceModel: String = "",
    val photos: List<PhotoPageItem> = emptyList(),
    val photoIndex: Int = 0,
    val sessionId: Int = 0,
    val importing: Boolean = false,
    val loadingPhoto: Boolean = false,
    val closing: Boolean = false,
    val closingInBackground: Boolean = false,
    val hasChanges: Boolean = false,
    val exportCompleted: Int = 0,
    val exportTotal: Int = 0,
    val exportRequiresJpeg: Boolean = false,
    val exportHasMotion: Boolean = false,
    val exportHasPortrait: Boolean = false,
    val exportRequiresAvif: Boolean = false,
    val separateLivePhoto: Boolean = false,
    val applePortrait: Boolean = false,
    val jpegQuality: Int = ing.fuyaoskyrocket.photoinfo.data.export.PhotoExporter.DEFAULT_JPEG_QUALITY,
) {
    val exportOptions get() = ing.fuyaoskyrocket.photoinfo.domain.model.ExportOptions(jpegQuality = jpegQuality,
        keepExif = keepCaptureMetadata, keepLocation = keepLocation, keepCaptureTime = keepCaptureTime,
        separateLivePhoto = separateLivePhoto, applePortrait = applePortrait)
    val canExport get() = photos.isNotEmpty() && !busy && !rendering &&
        (photos.size > 1 || (original != null && previewError == null && !preservationBlocked))
}
