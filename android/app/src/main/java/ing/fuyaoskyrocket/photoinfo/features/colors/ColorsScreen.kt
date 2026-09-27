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
        if (photos.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton({ onSelect(photoIndex - 1) }, enabled = !busy && photoIndex > 0) { Text(stringResource(R.string.previous_photo)) }
            Text(stringResource(R.string.photo_position, photoIndex + 1, photos.size))
            TextButton({ onSelect(photoIndex + 1) }, enabled = !busy && photoIndex < photos.lastIndex) { Text(stringResource(R.string.next_photo)) }
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
            { showingInfo = true }, model::setHDR, expandColorValues = true, modifier = modifier)
    }
    FuyaoScaffold("", showTopBar = false) { padding ->
        FuyaoAdaptivePage(padding, contentUnderTopEdge = true,
            single = { modifier ->
                BoxWithConstraints(modifier) {
                    val resultHeight = (maxHeight * .65f).coerceAtLeast(400.dp)
                    FuyaoPageColumn(Modifier.fillMaxSize()) {
                        overview()
                        if (photo != null) image(Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                        results(Modifier.fillMaxWidth().height(resultHeight))
                    }
                }
            },
            leading = { modifier -> FuyaoPageColumn(modifier) { overview(); image(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) } },
            trailing = { modifier -> results(modifier.padding(FuyaoSpacing.content)) })
    }
    if (showingInfo) model.info?.let { PhotoInfoSheet(it, model.hdr) { showingInfo = false } }
}
