package ing.fuyaoskyrocket.photoinfo.presentation

import android.content.Context
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.layout.CardOverflowException

internal enum class PhotoOperation(val title: Int, val recovery: Int) {
    OPEN(R.string.error_import_title, R.string.error_import),
    SAVE(R.string.error_export_title, R.string.error_export),
    FONT(R.string.error_font_title, R.string.error_font),
    PREVIEW(R.string.error_preview_title, R.string.error_preview),
}

/** Only application-authored explanations are safe to show as user-facing exception text. */
internal object PhotoFailureMessages {
    fun describe(context: Context, failure: Throwable, operation: PhotoOperation): String {
        if(failure is ing.fuyaoskyrocket.photoinfo.data.export.ExportStageException) {
            val cause=failure.cause
            if(cause is android.system.ErrnoException && cause.errno==android.system.OsConstants.ENOSPC)return context.getString(R.string.error_storage_full)
            if(cause is ing.fuyaoskyrocket.photoinfo.domain.layout.CardOverflowException)return context.getString(R.string.error_overflow)
            return context.getString(failure.stage)
        }
        val messageId = when (failure) {
            is CardOverflowException -> R.string.error_overflow
            is OutOfMemoryError -> R.string.error_memory
            is SecurityException -> R.string.error_permission
            else -> null
        }
        if (messageId != null) return context.getString(messageId)
        val explanations = listOf(R.string.video_metadata_unsupported, R.string.media_unsupported, R.string.hdr_requires_android14,
            R.string.image_encoder_unavailable,R.string.avif_precision_required,R.string.avif_requires_android14,
            R.string.live_pair_format,R.string.portrait_heic_required,R.string.heic_requires_android9,
            R.string.preservation_requires_jpeg, R.string.hdr_not_decoded, R.string.hdr_not_preserved,
            R.string.media_validation_failed).map(context::getString)
        return failure.message?.takeIf { it in explanations } ?: context.getString(operation.recovery)
    }
}
