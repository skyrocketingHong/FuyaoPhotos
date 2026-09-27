package ing.fuyaoskyrocket.photoinfo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.presentation.OriginalPhoto
import ing.fuyaoskyrocket.photoinfo.presentation.PhotoPageItem
import ing.fuyaoskyrocket.photoinfo.ui.components.OriginalPreviewState
import ing.fuyaoskyrocket.photoinfo.ui.components.PhotoInfoContent
import ing.fuyaoskyrocket.photoinfo.ui.components.PhotoInfoDisplay
import ing.fuyaoskyrocket.photoinfo.ui.components.MetadataEditPanel
import ing.fuyaoskyrocket.photoinfo.presentation.MetadataEditViewModel
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoMetadataScreen(photo: OriginalPhoto?, photos: List<PhotoPageItem>, photoIndex: Int,
    busy: Boolean, sharesCards: Boolean, controls: OriginalPreviewState, hdrAvailable: Boolean,
    onSelectPhoto: (Int) -> Unit, onGallery: () -> Unit, onFiles: () -> Unit,
    edit: MetadataEditViewModel = viewModel()) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val overview: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(FuyaoSpacing.content)) {
            FuyaoPageIntro(stringResource(R.string.photo_metadata_title),
                stringResource(if (sharesCards) R.string.metadata_shared_description else R.string.photo_metadata_description), R.drawable.ic_info) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onGallery, enabled = !busy) { Text(stringResource(R.string.from_gallery)) }
                    OutlinedButton(onClick = onFiles, enabled = !busy) { Text(stringResource(R.string.from_file)) }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(R.string.metadata_mode_view, R.string.metadata_mode_edit).forEachIndexed { index, title ->
                    SegmentedButton(selected = editing == (index == 1), onClick = { editing = index == 1 },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(stringResource(title)) }
                }
            }
            if (photos.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSelectPhoto(photoIndex - 1) }, enabled = !busy && photoIndex > 0) {
                    Icon(painterResource(R.drawable.ic_back), stringResource(R.string.previous_photo))
                }
                Text(stringResource(R.string.photo_position, photoIndex + 1, photos.size), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { onSelectPhoto(photoIndex + 1) }, enabled = !busy && photoIndex < photos.lastIndex) {
                    Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.next_photo))
                }
            }
        }
    }
    FuyaoScaffold("", showTopBar = false) { padding ->
        val summary: @Composable (Modifier, PhotoInfoDisplay) -> Unit = { modifier, display ->
            if (photo != null && editing) FuyaoPageColumn(modifier) {
                overview()
                MetadataEditPanel(photo, edit)
            }
            else if (photo != null) PhotoInfoContent(photo, controls, hdrAvailable, busy, modifier,
                display = display, header = overview)
            else FuyaoPageColumn(modifier) {
                overview()
                if (busy) {
                    CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.CenterHorizontally))
                    Text(stringResource(R.string.loading_photo), style = MaterialTheme.typography.bodyMedium)
                } else Text(stringResource(R.string.photo_metadata_empty), Modifier.padding(horizontal = FuyaoSpacing.cardInset),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FuyaoAdaptivePage(padding, contentUnderTopEdge = true,
            single = { summary(it, PhotoInfoDisplay.ALL) },
            leading = { summary(it, PhotoInfoDisplay.SUMMARY) },
            trailing = { modifier ->
                if (photo != null && editing) PhotoInfoContent(photo, controls, hdrAvailable, busy, modifier,
                    display = PhotoInfoDisplay.SUMMARY)
                else if (photo != null) PhotoInfoContent(photo, controls, hdrAvailable, busy, modifier,
                    display = PhotoInfoDisplay.FACTS)
            })
    }
}
