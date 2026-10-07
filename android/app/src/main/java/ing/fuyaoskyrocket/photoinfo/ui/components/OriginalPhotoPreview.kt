package ing.fuyaoskyrocket.photoinfo.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.presentation.OriginalPhoto
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.DevelopVeil
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoSpacing

enum class OriginalPreviewMode { PHOTO, MOTION, DEPTH }

@Stable
class OriginalPreviewState {
    var hdr by mutableStateOf(true)
    var mode by mutableStateOf(OriginalPreviewMode.PHOTO)
    var depthBitmap by mutableStateOf<Bitmap?>(null)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OriginalPhotoSummary(photo: OriginalPhoto, controls: OriginalPreviewState,
    subtitle: String, hdrAvailable: Boolean, busy: Boolean,
    workspaceModifier: Modifier? = null, expanded: Boolean = false,
    dissolveSource: String? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}) {
    val loadedDepth = rememberPortraitDepthLayer(photo.file.takeIf { photo.hasDepth && controls.depthBitmap == null })
    LaunchedEffect(loadedDepth) { if (loadedDepth != null) controls.depthBitmap = loadedDepth }
    val depth = controls.depthBitmap ?: loadedDepth
    val hdrSelected = controls.hdr && hdrAvailable && controls.mode != OriginalPreviewMode.DEPTH
    var playbackFailed by remember(photo.id) { mutableStateOf(false) }
    LaunchedEffect(busy) { if (busy && controls.mode == OriginalPreviewMode.MOTION) controls.mode = OriginalPreviewMode.PHOTO }
    DisposableEffect(photo.id) {
        onDispose { if (controls.mode == OriginalPreviewMode.MOTION) controls.mode = OriginalPreviewMode.PHOTO }
    }
    val media: @Composable BoxScope.() -> Unit = {
            DevelopVeil(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val displayed = if (controls.mode == OriginalPreviewMode.DEPTH) depth else photo.bitmap
                if (displayed != null) Image(displayed.asImageBitmap(),
                    stringResource(if (controls.mode == OriginalPreviewMode.DEPTH) R.string.portrait_depth_preview else R.string.original_preview),
                    Modifier.fillMaxSize().then(if (dissolveSource != null) Modifier.photoDissolveSource(dissolveSource, displayed) else Modifier), contentScale = ContentScale.Fit)
                else if (busy) CircularProgressIndicator(Modifier.size(28.dp))
                else Text(stringResource(R.string.error_preview_title), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (controls.mode == OriginalPreviewMode.MOTION && photo.motion != null) {
                    MotionPhotoPreview(photo.motion, Modifier.fillMaxSize(),
                        onFinished = { if (controls.mode == OriginalPreviewMode.MOTION) controls.mode = OriginalPreviewMode.PHOTO },
                        onError = { controls.mode = OriginalPreviewMode.PHOTO; playbackFailed = true })
                }
            }
            overlay()
    }
    val tools: @Composable RowScope.() -> Unit = {
        if (photo.hdr) PreviewMediaButton(R.drawable.ic_hdr,
            stringResource(if (!hdrAvailable) R.string.hdr_unavailable else if (hdrSelected) R.string.disable_hdr else R.string.enable_hdr),
            hdrSelected, {
                controls.hdr = if (controls.mode == OriginalPreviewMode.DEPTH) true else !controls.hdr
                controls.mode = OriginalPreviewMode.PHOTO
            }, enabled = hdrAvailable && !busy)
        if (photo.motion != null) PreviewMediaButton(R.drawable.ic_motion, stringResource(R.string.media_motion),
            controls.mode == OriginalPreviewMode.MOTION, {
                controls.mode = if (controls.mode == OriginalPreviewMode.MOTION) OriginalPreviewMode.PHOTO else OriginalPreviewMode.MOTION
            }, enabled = !busy)
        if (depth != null) PreviewMediaButton(R.drawable.ic_photo_info, stringResource(R.string.portrait_depth_tag),
            controls.mode == OriginalPreviewMode.DEPTH, {
                controls.mode = if (controls.mode == OriginalPreviewMode.DEPTH) OriginalPreviewMode.PHOTO else OriginalPreviewMode.DEPTH
            }, enabled = !busy)
        actions()
    }
    if (workspaceModifier != null) {
        PhotoPreviewStage(workspaceModifier, expanded, media, tools)
    } else Box(Modifier.fillMaxWidth()) {
        PhotoAmbientBackdrop(photo.bitmap, modifier = Modifier.matchParentSize())
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f), content = media)
            Column(Modifier.padding(horizontal = FuyaoSpacing.cardInset), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(photo.details.displayName ?: stringResource(R.string.photo_details), style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = tools)
                if (controls.mode == OriginalPreviewMode.DEPTH) Text(stringResource(R.string.portrait_depth_disparity_note),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (playbackFailed) AlertDialog(onDismissRequest = { playbackFailed = false },
        title = { Text(stringResource(R.string.motion_playback_title)) }, text = { Text(stringResource(R.string.motion_playback_error)) },
        confirmButton = { TextButton(onClick = { playbackFailed = false }) { Text(stringResource(R.string.close)) } })
}
