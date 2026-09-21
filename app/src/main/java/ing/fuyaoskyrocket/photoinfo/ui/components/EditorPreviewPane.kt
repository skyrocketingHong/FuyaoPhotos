package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.flow.collect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.presentation.EditorState
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoIconButton
import kotlinx.coroutines.delay
import ing.fuyaoskyrocket.photoinfo.platform.MotionClipSource

@Composable
fun EditorPreviewPane(state: EditorState, onSelectPhoto: (Int) -> Unit, original: Boolean, onOriginal: () -> Unit,
    onEnlarge: () -> Unit, motion: MotionClipSource?, showHdr: Boolean, hdrEnabled: Boolean, hdrAvailable: Boolean, onHdr: () -> Unit,
    modifier: Modifier = Modifier, bottomSafe: Boolean = false) {
    val photoId=state.photos.getOrNull(state.photoIndex)?.id
    var playing by remember(photoId) { mutableStateOf(false) }
    var playbackError by remember(photoId) { mutableStateOf(false) }
    LaunchedEffect(state.busy,state.rendering,motion) { if(state.busy || state.rendering || motion==null)playing=false }
    val scope=rememberCoroutineScope()
    val previousLabel=stringResource(R.string.previous_photo)
    val nextLabel=stringResource(R.string.next_photo)
    val positionLabel=stringResource(R.string.photo_position,state.photoIndex+1,state.photos.size)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val selectedIndex by rememberUpdatedState(state.photoIndex)
    var delayedRendering by remember { mutableStateOf(false) }
    LaunchedEffect(state.busy, state.rendering) {
        delayedRendering = false
        if (state.rendering && !state.busy) { delay(200); delayedRendering = true }
    }
    val media = state.mediaMessage?.let { stringResource(it) }
    val details = state.previewError ?: media?.takeIf { state.preservationBlocked }
    var showDetails by remember(details) { mutableStateOf(false) }
    val bottom = if (bottomSafe) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
    BoxWithConstraints(modifier) {
        // Fit the whole 4:3 viewport when the keyboard or landscape window limits height.
        val frameWidth = minOf(maxWidth, (maxHeight - bottom).coerceAtLeast(0.dp) * 4f / 3f)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(frameWidth * 3f / 4f)) {
                key(state.sessionId) {
                    val pager = rememberPagerState(initialPage = state.photoIndex) { state.photos.size }
                    LaunchedEffect(pager.isScrollInProgress) { if(pager.isScrollInProgress)playing=false }
                    LaunchedEffect(pager) {
                        snapshotFlow { pager.settledPage }.collect { page ->
                            if (page != selectedIndex) {
                                focus.clearFocus(force = true)
                                keyboard?.hide()
                                onSelectPhoto(page)
                            }
                        }
                    }
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().semantics {
                        stateDescription=positionLabel
                        customActions=buildList {
                            if(!state.busy && state.photoIndex>0)add(CustomAccessibilityAction(previousLabel) {
                                scope.launch { pager.scrollToPage(state.photoIndex-1) };true
                            })
                            if(!state.busy && state.photoIndex<state.photos.lastIndex)add(CustomAccessibilityAction(nextLabel) {
                                scope.launch { pager.scrollToPage(state.photoIndex+1) };true
                            })
                        }
                    }, key = { state.photos[it].id },
                        userScrollEnabled = (!state.busy || state.loadingPhoto) && !state.closing) { page ->
                        if (page == state.photoIndex) {
                            Box(Modifier.fillMaxSize()) {
                                PhotoPreview(if (original) state.original else state.preview, Modifier.fillMaxSize())
                                if(playing && motion!=null)MotionPhotoPreview(motion,Modifier.fillMaxSize(),
                                    onFinished={ playing=false },onError={ playing=false;playbackError=true })
                            }
                        } else Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
                Surface(Modifier.align(Alignment.TopCenter),color=Color.Black.copy(alpha=.68f),contentColor=Color.White) {
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(start=12.dp,end=0.dp),verticalAlignment=Alignment.CenterVertically) {
                        val dimensions=stringResource(R.string.photo_dimensions,state.width,state.height)
                        val position=if(state.photos.size>1)stringResource(R.string.status_separator,
                            stringResource(R.string.photo_progress,state.photoIndex+1,state.photos.size),dimensions) else dimensions
                        val status=when {
                            state.exporting -> stringResource(R.string.batch_progress,state.exportCompleted,state.exportTotal)
                            state.importing -> stringResource(R.string.importing_photos)
                            state.loadingPhoto -> stringResource(R.string.loading_photo)
                            else -> details ?: position
                        }
                        Text(status,Modifier.weight(1f).semantics { liveRegion=LiveRegionMode.Polite }
                            .then(if(details==null)Modifier else Modifier.clickable { showDetails=true }),
                            style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis,
                            color=if(details==null)Color.White else MaterialTheme.colorScheme.error)
                        if(state.motionPhoto)PreviewMediaButton(if(playing)R.drawable.ic_stop else R.drawable.ic_motion,
                            stringResource(if(playing)R.string.stop_motion else R.string.play_motion),playing,{ playing=!playing },
                            enabled=motion!=null && !state.busy && !state.rendering)
                        if(showHdr)PreviewMediaButton(R.drawable.ic_hdr,
                            stringResource(if(!hdrAvailable)R.string.hdr_unavailable else if(hdrEnabled)R.string.disable_hdr else R.string.enable_hdr),
                            hdrEnabled && hdrAvailable,{ playing=false;onHdr() },enabled=hdrAvailable)
                        PreviewMediaButton(R.drawable.ic_compare,stringResource(R.string.original),original,
                            { playing=false;onOriginal() },enabled=state.original!=null)
                        FuyaoIconButton(R.drawable.ic_expand,stringResource(R.string.enlarge),{ playing=false;onEnlarge() },enabled=state.preview!=null)
                    }
                }
                if (state.exporting && state.exportTotal > 0) LinearProgressIndicator(
                    progress={ state.exportCompleted.toFloat()/state.exportTotal },
                    modifier=Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                else if (state.busy || delayedRendering) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomCenter))
            }
        }
    }
    if(playbackError)AlertDialog(onDismissRequest={ playbackError=false },
        title={ Text(stringResource(R.string.motion_playback_title)) },text={ Text(stringResource(R.string.motion_playback_error)) },
        confirmButton={ TextButton(onClick={ playbackError=false }) { Text(stringResource(R.string.close)) } })
    if (showDetails && details != null) AlertDialog(onDismissRequest = { showDetails = false },
        title = { Text(stringResource(R.string.error_preview_title)) }, text = { Text(details) },
        confirmButton = { TextButton(onClick = { showDetails = false }) { Text(stringResource(R.string.close)) } })
}
