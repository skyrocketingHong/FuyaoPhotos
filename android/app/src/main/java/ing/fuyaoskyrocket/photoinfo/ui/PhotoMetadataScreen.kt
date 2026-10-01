package ing.fuyaoskyrocket.photoinfo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.presentation.OriginalPhoto
import ing.fuyaoskyrocket.photoinfo.presentation.PhotoPageItem
import ing.fuyaoskyrocket.photoinfo.presentation.MetadataEditViewModel
import ing.fuyaoskyrocket.photoinfo.ui.components.*
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoMetadataScreen(photo: OriginalPhoto?, photos: List<PhotoPageItem>, photoIndex: Int,
    busy: Boolean, sharesCards: Boolean, controls: OriginalPreviewState, hdrAvailable: Boolean,
    onSelectPhoto: (Int) -> Unit, onGallery: () -> Unit, onFiles: () -> Unit,
    edit: MetadataEditViewModel = viewModel()) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var showOpen by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val modeControls: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (photos.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ onSelectPhoto(photoIndex - 1) }, enabled = !busy && photoIndex > 0) {
                    Icon(painterResource(R.drawable.ic_back), stringResource(R.string.previous_photo))
                }
                Text(stringResource(R.string.photo_position, photoIndex + 1, photos.size), Modifier.weight(1f))
                IconButton({ onSelectPhoto(photoIndex + 1) }, enabled = !busy && photoIndex < photos.lastIndex) {
                    Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.next_photo))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    listOf(R.string.metadata_mode_view, R.string.metadata_mode_edit).forEachIndexed { index, title ->
                        SegmentedButton(selected = editing == (index == 1), onClick = { focus.clearFocus(); editing = index == 1 },
                            shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(stringResource(title)) }
                    }
                }
                Box {
                    IconButton({ showOpen = true }, enabled = !busy) {
                        Icon(painterResource(R.drawable.ic_photo_add), stringResource(R.string.from_gallery))
                    }
                    DropdownMenu(showOpen, { showOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.from_gallery)) }, onClick = { showOpen = false; onGallery() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.from_file)) }, onClick = { showOpen = false; onFiles() })
                    }
                }
            }
        }
    }
    FuyaoScaffold("", showTopBar = false) { padding ->
        if (photo == null) {
            FuyaoPageColumn(Modifier.fillMaxSize().consumeWindowInsets(padding).imePadding(), topInset=padding.calculateTopPadding()) {
                PhotoImportCard(stringResource(R.string.photo_metadata_title),
                    stringResource(if (sharesCards) R.string.metadata_shared_description else R.string.photo_metadata_description),
                    R.drawable.ic_info,busy,onGallery,onFiles)
            }
        } else {
            FuyaoAdaptivePage(padding, contentUnderTopEdge = true,
                single = { modifier ->
                    PhotoInfoContent(photo, controls, hdrAvailable, busy, modifier,
                        display = if (editing) PhotoInfoDisplay.SUMMARY else PhotoInfoDisplay.ALL,
                        afterSummary = { modeControls() },
                        belowBar = if (editing) {
                            { MetadataEditPanel(photo, edit) }
                        } else null)
                },
                leading = { modifier -> PhotoInfoContent(photo, controls, hdrAvailable, busy, modifier,
                    display = PhotoInfoDisplay.SUMMARY, afterSummary = modeControls) },
                trailing = { modifier ->
                    if (editing) FuyaoPageColumn(modifier) { MetadataEditPanel(photo, edit) }
                    else PhotoInfoContent(photo, controls, hdrAvailable, busy, modifier, display = PhotoInfoDisplay.FACTS)
                })
        }
    }
}
