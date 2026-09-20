package ing.fuyaoskyrocket.photoinfo.presentation

import android.graphics.Bitmap
import android.net.Uri
import ing.fuyaoskyrocket.photoinfo.domain.model.CardStyle
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportFormat
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoInfo

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
    val keepCaptureMetadata: Boolean = true,
    val exported: ExportedPhoto? = null,
) {
    val canExport get() = original != null && !busy && !rendering && previewError == null
}
