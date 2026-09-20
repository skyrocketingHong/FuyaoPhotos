package ing.fuyaoskyrocket.photoinfo.presentation

import android.graphics.Bitmap
import android.net.Uri
import ing.fuyaoskyrocket.photoinfo.domain.model.CardStyle
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportFormat
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoInfo
import ing.fuyaoskyrocket.photoinfo.domain.model.EditorSettings

enum class LocationStatus { IDLE, RESOLVING, RESOLVED, UNAVAILABLE, NO_GPS, DISABLED }

data class ExportedPhoto(val uri: Uri, val format: ExportFormat)
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
    val previewError: String? = null,
    val notice: String? = null,
    val fontName: String? = null,
    val hasCustomFont: Boolean = false,
    val keepCaptureMetadata: Boolean = true,
    val exported: ExportedPhoto? = null,
    val settings: EditorSettings = EditorSettings(),
    val locationStatus: LocationStatus = LocationStatus.IDLE,
    val hasPhotoGps: Boolean = false,
    val preservationBlocked: Boolean = false,
    val jpegRequired: Boolean = false,
    val motionPhoto: Boolean = false,
    val mediaMessage: Int? = null,
    val sourceDevice: String = "",
) {
    val canExport get() = original != null && !busy && !rendering && previewError == null && !preservationBlocked
}
