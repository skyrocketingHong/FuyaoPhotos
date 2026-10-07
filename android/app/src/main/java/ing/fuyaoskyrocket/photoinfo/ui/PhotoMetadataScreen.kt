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
    onClose: (() -> Unit)? = null,
    edit: MetadataEditViewModel = viewModel()) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var showOpen by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    FuyaoScaffold("", showTopBar = false) { padding ->
        if (photo == null) {
            FuyaoFormPage(padding) {
                PhotoImportCard(stringResource(R.string.photo_metadata_title),
                    stringResource(if (sharesCards) R.string.metadata_shared_description else R.string.photo_metadata_description),
                    R.drawable.ic_info,busy,onGallery,onFiles)
            }
        } else {
            PhotoWorkspaceCanvas(photo.bitmap, padding) {
                EditorWorkspace(controlsUnderNavigation = true, preview = { modifier, expanded ->
                    OriginalPhotoSummary(photo, controls, "", hdrAvailable, busy,
                        workspaceModifier = modifier, expanded = expanded,
                        dissolveSource = "metadata-photo",
                        overlay = { PhotoPageNavigation(photos.size, photoIndex, busy, onSelectPhoto) },
                        actions = {
                            Text(photo.details.displayName ?: stringResource(R.string.photo_details),
                                Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Box {
                                IconButton({ showOpen = true }, enabled = !busy) {
                                    Icon(painterResource(R.drawable.ic_photo_add), stringResource(R.string.from_gallery))
                                }
                                DropdownMenu(showOpen, { showOpen = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.from_gallery)) },
                                        onClick = { showOpen = false; onGallery() })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.from_file)) },
                                        onClick = { showOpen = false; onFiles() })
                                }
                            }
                            if (onClose != null) IconButton(onClick = onClose, enabled = !edit.busy) {
                                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close_photo))
                            }
                        })
                }, controls = { modifier ->
                    Column(modifier) {
                        FuyaoPrimaryTabs(listOf(stringResource(R.string.metadata_mode_view), stringResource(R.string.metadata_mode_edit)),
                            if (editing) 1 else 0, { focus.clearFocus(); editing = it == 1 })
                        if (controls.mode == OriginalPreviewMode.DEPTH) {
                            Text(stringResource(R.string.portrait_depth_disparity_note),
                                Modifier.padding(horizontal = FuyaoSpacing.content),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (editing) FuyaoPageColumn(Modifier.weight(1f), topInset = 0.dp) { MetadataEditPanel(photo, edit, sourceBusy = busy) }
                        else PhotoInfoContent(photo, controls, hdrAvailable, busy, Modifier.weight(1f),
                            topInset = 0.dp, display = PhotoInfoDisplay.FACTS)
                    }
                })
            }
        }
    }
}
