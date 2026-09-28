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
import ing.fuyaoskyrocket.photoinfo.ui.components.PhotoAmbientBackdrop
import ing.fuyaoskyrocket.photoinfo.ui.components.EditorWorkspace
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
        FuyaoPageIntro(stringResource(R.string.colors_title), stringResource(R.string.colors_description), R.drawable.cp_ic_colorize) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onGallery, enabled = !busy) { Text(stringResource(R.string.from_gallery)) }
                OutlinedButton(onFiles, enabled = !busy) { Text(stringResource(R.string.from_file)) }
                OutlinedButton(onCamera, enabled = !busy) { Text(stringResource(R.string.colors_camera)) }
            }
        }
    }
    @Composable fun image(modifier: Modifier) {
        Box(modifier) {
            PhotoAmbientBackdrop(photo?.bitmap, modifier = Modifier.matchParentSize())
            model.bitmap?.let { bitmap ->
                SampleImagePanel(bitmap, model.sample, model.transform, model::updateTransform, model::sampleAt,
                    Modifier.fillMaxSize())
            }
            if (model.busy || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (model.error != null) Text(model.error.orEmpty(), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
        }
    }
    @Composable fun results(modifier: Modifier) {
        ColorResultPanel(model.bitmap, model.sample, model.info, model.hdr,
            { showingInfo = true }, model::setHDR, expandColorValues = true, modifier = modifier,
            sourceProfile = photo?.details?.colorSpace ?: model.info?.colorSpaceName,
            photoActions = { ColorPhotoMenu(busy, model.info != null, onGallery, onFiles, onCamera) { showingInfo = true } })
    }
    @Composable fun photoTools() {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (photos.size > 1) {
                IconButton({ onSelect(photoIndex - 1) }, enabled = !busy && photoIndex > 0) {
                    Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_back), stringResource(R.string.previous_photo))
                }
                Text(stringResource(R.string.photo_position, photoIndex + 1, photos.size), Modifier.weight(1f))
                IconButton({ onSelect(photoIndex + 1) }, enabled = !busy && photoIndex < photos.lastIndex) {
                    Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_chevron), stringResource(R.string.next_photo))
                }
            }
        }
    }
    FuyaoScaffold("", showTopBar = false) { padding ->
        if (photo == null) {
            FuyaoPageColumn(Modifier.fillMaxSize().padding(padding)) { overview() }
        } else Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            EditorWorkspace(
                preview = { modifier, _ ->
                    Box(modifier.padding(horizontal = FuyaoSpacing.content)) {
                        image(Modifier.fillMaxSize())
                        if (photos.size > 1) Box(Modifier.align(Alignment.BottomCenter)) { photoTools() }
                    }
                },
                controls = { modifier -> results(modifier.padding(horizontal = FuyaoSpacing.content)) })
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
