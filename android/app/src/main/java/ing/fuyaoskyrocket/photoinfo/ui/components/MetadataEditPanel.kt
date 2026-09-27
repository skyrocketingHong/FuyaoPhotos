package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportFormat
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportOptions
import ing.fuyaoskyrocket.photoinfo.presentation.MetadataEditViewModel
import ing.fuyaoskyrocket.photoinfo.presentation.OriginalPhoto
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoSpacing

@Composable
fun MetadataEditPanel(photo: OriginalPhoto, model: MetadataEditViewModel) {
    val options = model.options(photo)
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { directory ->
        if (directory != null) model.save(photo, options, directory)
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(FuyaoSpacing.cardInset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.metadata_edit_description), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExportOptionsControls(options, { model.change(photo, it) }, jpegRequired = photo.hdr || photo.hasDepth || photo.motion != null,
                hasMotion = photo.motion != null, hasPortrait = photo.hasDepth,
                showLiveOption = photo.motion != null, showPortraitOption = photo.hasDepth,
                avifRequired = photo.bitDepth > 8, editMetadata = true)
            val compatible = ing.fuyaoskyrocket.photoinfo.platform.ImageEncoderSupport.supports(options.format) &&
                (photo.bitDepth <= 8 || options.format in setOf(ExportFormat.HEIC, ExportFormat.AVIF)) &&
                (!photo.hasDepth || options.format == if (options.applePortrait) ExportFormat.HEIC else ExportFormat.JPEG)
            Button(onClick = {
                if (options.separateLivePhoto && photo.motion != null) folder.launch(null)
                else model.save(photo, options)
            }, enabled = !model.busy && compatible && model.hasChanges(photo.id), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.metadata_save_copy))
            }
            if (!compatible) Text(stringResource(R.string.export_formats_conflict), color = MaterialTheme.colorScheme.error)
            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if (model.error != null || model.saved) AlertDialog(onDismissRequest = model::dismissResult,
        title = { Text(stringResource(if (model.saved) R.string.export_success else R.string.error_export_title)) },
        text = { if (model.error != null) Text(model.error.orEmpty()) },
        confirmButton = { TextButton(onClick = model::dismissResult) { Text(stringResource(R.string.close)) } })
}
