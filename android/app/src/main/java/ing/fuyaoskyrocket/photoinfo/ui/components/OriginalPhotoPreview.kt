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
    subtitle: String, hdrAvailable: Boolean, busy: Boolean) {
    val loadedDepth = rememberPortraitDepthLayer(photo.file.takeIf { photo.hasDepth && controls.depthBitmap == null })
    LaunchedEffect(loadedDepth) { if (loadedDepth != null) controls.depthBitmap = loadedDepth }
    val depth = controls.depthBitmap ?: loadedDepth
    val hdrSelected = controls.hdr && hdrAvailable && controls.mode != OriginalPreviewMode.DEPTH
    var playbackFailed by remember(photo.id) { mutableStateOf(false) }
    LaunchedEffect(busy) { if (busy && controls.mode == OriginalPreviewMode.MOTION) controls.mode = OriginalPreviewMode.PHOTO }
    DisposableEffect(photo.id) {
        onDispose { if (controls.mode == OriginalPreviewMode.MOTION) controls.mode = OriginalPreviewMode.PHOTO }
    }
    Box(Modifier.fillMaxWidth()) {
        PhotoAmbientBackdrop(photo.bitmap, modifier = Modifier.matchParentSize())
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DevelopVeil(Modifier.fillMaxWidth().aspectRatio(4f / 3f), contentAlignment = Alignment.Center) {
                val displayed = if (controls.mode == OriginalPreviewMode.DEPTH) depth else photo.bitmap
                if (displayed != null) Image(displayed.asImageBitmap(),
                    stringResource(if (controls.mode == OriginalPreviewMode.DEPTH) R.string.portrait_depth_preview else R.string.original_preview),
                    Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else if (busy) CircularProgressIndicator(Modifier.size(28.dp))
                else Text(stringResource(R.string.error_preview_title), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (controls.mode == OriginalPreviewMode.MOTION && photo.motion != null) {
                    MotionPhotoPreview(photo.motion, Modifier.fillMaxSize(),
                        onFinished = { if (controls.mode == OriginalPreviewMode.MOTION) controls.mode = OriginalPreviewMode.PHOTO },
                        onError = { controls.mode = OriginalPreviewMode.PHOTO; playbackFailed = true })
                }
            }
            Column(Modifier.padding(horizontal = FuyaoSpacing.cardInset), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(photo.details.displayName ?: stringResource(R.string.photo_details), style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (photo.hdr) FilterChip(selected = hdrSelected,
                        onClick = {
                            controls.hdr = if (controls.mode == OriginalPreviewMode.DEPTH) true else !controls.hdr
                            controls.mode = OriginalPreviewMode.PHOTO
                        }, enabled = hdrAvailable && !busy,
                        label = { Text(stringResource(R.string.media_hdr)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_hdr),
                            stringResource(if (!hdrAvailable) R.string.hdr_unavailable else if (hdrSelected) R.string.disable_hdr else R.string.enable_hdr), Modifier.size(20.dp)) })
                    if (photo.motion != null) FilterChip(selected = controls.mode == OriginalPreviewMode.MOTION,
                        onClick = { controls.mode = if (controls.mode == OriginalPreviewMode.MOTION) OriginalPreviewMode.PHOTO else OriginalPreviewMode.MOTION },
                        enabled = !busy, label = { Text(stringResource(R.string.media_motion)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_motion), null, Modifier.size(20.dp)) })
                    if (depth != null) FilterChip(selected = controls.mode == OriginalPreviewMode.DEPTH,
                        onClick = { controls.mode = if (controls.mode == OriginalPreviewMode.DEPTH) OriginalPreviewMode.PHOTO else OriginalPreviewMode.DEPTH },
                        enabled = !busy, label = { Text(stringResource(R.string.portrait_depth_tag)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_photo_info), null, Modifier.size(20.dp)) })
                }
                if (controls.mode == OriginalPreviewMode.DEPTH) Text(stringResource(R.string.portrait_depth_disparity_note),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (playbackFailed) AlertDialog(onDismissRequest = { playbackFailed = false },
        title = { Text(stringResource(R.string.motion_playback_title)) }, text = { Text(stringResource(R.string.motion_playback_error)) },
        confirmButton = { TextButton(onClick = { playbackFailed = false }) { Text(stringResource(R.string.close)) } })
}
