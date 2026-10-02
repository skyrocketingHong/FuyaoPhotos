package ing.fuyaoskyrocket.photoinfo.features.colors

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.features.colors.ui.photo.SampleImagePanel
import ing.fuyaoskyrocket.photoinfo.features.colors.ui.components.ColorResultPanel
import ing.fuyaoskyrocket.photoinfo.features.colors.ui.components.PhotoInfoSheet
import ing.fuyaoskyrocket.photoinfo.presentation.OriginalPhoto
import ing.fuyaoskyrocket.photoinfo.presentation.PhotoPageItem
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.*
import ing.fuyaoskyrocket.photoinfo.ui.components.PhotoWorkspaceCanvas
import ing.fuyaoskyrocket.photoinfo.ui.components.EditorWorkspace
import ing.fuyaoskyrocket.photoinfo.ui.components.PhotoPreviewStage
import ing.fuyaoskyrocket.photoinfo.ui.components.PhotoPageNavigation
import androidx.compose.ui.Alignment

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorsScreen(photo: OriginalPhoto?, photos: List<PhotoPageItem>, photoIndex: Int, busy: Boolean,
    onSelect: (Int) -> Unit, onGallery: () -> Unit, onFiles: () -> Unit, onCamera: () -> Unit,
    onHDR: (Boolean, Boolean) -> Unit) {
    val model: ColorSamplingViewModel = viewModel()
    var showingInfo by remember { mutableStateOf(false) }
    LaunchedEffect(photo?.id) { model.load(photo) }
    LaunchedEffect(model.hdr, model.info?.hasHdrContent) { onHDR(model.info?.hasHdrContent == true, model.hdr) }
    @Composable fun overview() {
        PhotoImportCard(stringResource(R.string.colors_title), stringResource(R.string.colors_description),
            R.drawable.cp_ic_colorize,busy,onGallery,onFiles,onCamera)
    }
    @Composable fun image(modifier: Modifier) {
        Box(modifier) {
            model.bitmap?.let { bitmap ->
                SampleImagePanel(bitmap, model.sample, model.transform, model::updateTransform, model::sampleAt,
                    Modifier.fillMaxSize())
            }
            if (model.busy || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (model.error != null) Text(model.error.orEmpty(), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
        }
    }
    @Composable fun results(modifier: Modifier) {
        ColorResultPanel(model.bitmap, model.sample, model.info, modifier = modifier,
            sourceProfile = photo?.details?.colorSpace ?: model.info?.colorSpaceName)
    }
    @Composable fun photoTools() {
        ColorPhotoMenu(busy, model.info != null, onGallery, onFiles, onCamera) { showingInfo = true }
        ing.fuyaoskyrocket.photoinfo.ui.components.PreviewMediaButton(R.drawable.ic_hdr,
            stringResource(R.string.cp_photo_hdr), model.hdr, { model.setHDR(!model.hdr) },
            enabled = !busy && model.info?.hasHdrContent == true)
        IconButton({ showingInfo = true }, enabled = model.info != null) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_info), stringResource(R.string.cp_photo_info_action))
        }
    }
    FuyaoScaffold("", showTopBar = false) { padding ->
        if (photo == null) {
            FuyaoFormPage(padding) { overview() }
        } else PhotoWorkspaceCanvas(photo.bitmap, padding) {
            EditorWorkspace(
                preview = { modifier, expanded ->
                    PhotoPreviewStage(modifier, expanded, media = {
                        image(Modifier.fillMaxSize())
                        PhotoPageNavigation(photos.size, photoIndex, busy, onSelect)
                    }, actions = { photoTools() })
                },
                controls = { modifier -> results(modifier) })
        }
    }
    if (showingInfo) model.info?.let { PhotoInfoSheet(it, model.hdr) { showingInfo = false } }
}

@Composable
private fun ColorPhotoMenu(busy: Boolean, hasInfo: Boolean, gallery: () -> Unit, files: () -> Unit, camera: () -> Unit, info: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton({ expanded = true }, enabled = !busy) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_photo_add), stringResource(R.string.from_gallery))
        }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.from_gallery)) }, onClick = { expanded = false; gallery() })
            DropdownMenuItem(text = { Text(stringResource(R.string.from_file)) }, onClick = { expanded = false; files() })
            DropdownMenuItem(text = { Text(stringResource(R.string.colors_camera)) }, onClick = { expanded = false; camera() })
            HorizontalDivider()
            DropdownMenuItem(text = { Text(stringResource(R.string.cp_photo_info_action)) }, enabled = hasInfo, onClick = { expanded = false; info() })
        }
    }
}
