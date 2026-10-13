package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.presentation.EditorState
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.DevelopVeil
import ing.fuyaoskyrocket.photoinfo.platform.MotionClipSource
import ing.fuyaoskyrocket.photoinfo.ui.theme.LocalPhotoMotionEnabled
import kotlinx.coroutines.launch

@Composable
fun EditorPreviewPane(state:EditorState,onSelectPhoto:(Int)->Unit,original:Boolean,onOriginal:()->Unit,
    onEnlarge:()->Unit,motion:MotionClipSource?,showHdr:Boolean,hdrEnabled:Boolean,hdrAvailable:Boolean,onHdr:()->Unit,
    onClose:()->Unit,closeEnabled:Boolean,
    modifier:Modifier=Modifier,bottomSafe:Boolean=false,editingText:Boolean=false,
    actions:@Composable RowScope.()->Unit={}) {
    val photoId=state.photos.getOrNull(state.photoIndex)?.id
    var playing by remember(photoId) { mutableStateOf(false) }
    var playbackError by remember(photoId) { mutableStateOf(false) }
    LaunchedEffect(state.busy,state.rendering,motion) { if(state.busy || state.rendering || motion==null)playing=false }
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val selectedIndex by rememberUpdatedState(state.photoIndex)
    val scope=rememberCoroutineScope()
    val motionEnabled = LocalPhotoMotionEnabled.current
    val previous=stringResource(R.string.previous_photo)
    val next=stringResource(R.string.next_photo)
    val position=stringResource(R.string.photo_position,state.photoIndex+1,state.photos.size)
    val updating=state.rendering || editingText
    PhotoPreviewStage(modifier, expanded = bottomSafe, media = {
            Box(Modifier.matchParentSize()) {
                val pager=rememberPagerState(initialPage=state.photoIndex) { state.photos.size }
                fun move(delta: Int) {
                    playing = false
                    focus.clearFocus(force = true)
                    keyboard?.hide()
                    scope.launch {
                        val page = (pager.currentPage + delta).coerceIn(0, state.photos.lastIndex)
                        if (motionEnabled) pager.animateScrollToPage(page) else pager.scrollToPage(page)
                    }
                }
                LaunchedEffect(pager.isScrollInProgress) { if(pager.isScrollInProgress)playing=false }
                LaunchedEffect(pager) {
                    snapshotFlow { pager.settledPage }.collect { page ->
                        if(page!=selectedIndex) { focus.clearFocus(force=true);keyboard?.hide();onSelectPhoto(page) }
                    }
                }
                DevelopVeil(Modifier.fillMaxSize()) {
                HorizontalPager(state=pager,key={ state.photos[it].id },modifier=Modifier.fillMaxSize().semantics {
                    stateDescription=position
                    customActions=buildList {
                        if(!state.busy && state.photoIndex>0)add(CustomAccessibilityAction(previous) { scope.launch { pager.scrollToPage(state.photoIndex-1) };true })
                        if(!state.busy && state.photoIndex<state.photos.lastIndex)add(CustomAccessibilityAction(next) { scope.launch { pager.scrollToPage(state.photoIndex+1) };true })
                    }
                },userScrollEnabled=(!state.busy || state.loadingPhoto) && !state.closing) { page ->
                    if(page==state.photoIndex)PendingPhotoEffect(updating,Modifier.fillMaxSize()) {
                        val displayed = if(original && !state.exporting)state.original else state.preview
                        PhotoPreview(displayed,Modifier.fillMaxSize().photoDissolveSource("cards-photo", displayed),
                            showsBackdrop=false,original=original)
                        if(playing && motion!=null)MotionPhotoPreview(motion,Modifier.fillMaxSize(),
                            onFinished={ playing=false },onError={ playing=false;playbackError=true })
                    } else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_photo_info),stringResource(R.string.loading_photo),
                            Modifier.size(28.dp),tint=MaterialTheme.colorScheme.primary)
                    }
                }
                if(state.photos.size>1) {
                    val navColors=IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor=MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha=.84f))
                    Row(Modifier.fillMaxSize().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        FilledTonalIconButton(onClick = { move(-1) },
                            enabled=!state.busy && pager.currentPage>0,colors=navColors,modifier=Modifier.size(48.dp)) {
                            Icon(painterResource(R.drawable.ic_back),previous,Modifier.size(20.dp))
                        }
                        FilledTonalIconButton(onClick = { move(1) },
                            enabled=!state.busy && pager.currentPage<state.photos.lastIndex,colors=navColors,modifier=Modifier.size(48.dp)) {
                            Icon(painterResource(R.drawable.ic_chevron),next,Modifier.size(20.dp))
                        }
                    }
                    Text(position,Modifier.align(Alignment.BottomCenter).padding(bottom=8.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha=.84f),MaterialTheme.shapes.extraLarge)
                        .padding(horizontal=10.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium)
                }
                }
            }
    }, actions = {
        val tools = buildList {
            if (state.motionPhoto) add(EditorToolAction(EditorTool.MOTION,
                if (playing) R.drawable.ic_stop else R.drawable.ic_motion,
                stringResource(if (playing) R.string.stop_motion else R.string.play_motion),
                enabled = motion != null && !state.busy && !state.rendering, checked = playing) { playing = !playing })
            if (showHdr) add(EditorToolAction(EditorTool.HDR, R.drawable.ic_hdr,
                stringResource(if (!hdrAvailable) R.string.hdr_unavailable else if (hdrEnabled) R.string.disable_hdr else R.string.enable_hdr),
                enabled = hdrAvailable && !state.busy, checked = hdrEnabled && hdrAvailable) { playing = false; onHdr() })
            add(EditorToolAction(EditorTool.ORIGINAL, R.drawable.ic_compare, stringResource(R.string.original),
                enabled = state.original != null && !state.busy, checked = original) { playing = false; onOriginal() })
            add(EditorToolAction(EditorTool.CLOSE, R.drawable.ic_close, stringResource(R.string.close_photo),
                enabled = closeEnabled) { playing = false; onClose() })
            add(EditorToolAction(EditorTool.FULLSCREEN, R.drawable.ic_expand, stringResource(R.string.enlarge),
                enabled = state.preview != null && !state.busy) { playing = false; onEnlarge() })
        }
        EditorActionStrip(tools, primaryActions = actions)
    })
    if(playbackError)AlertDialog(onDismissRequest={ playbackError=false },title={ Text(stringResource(R.string.motion_playback_title)) },
        text={ Text(stringResource(R.string.motion_playback_error)) },confirmButton={ TextButton(onClick={ playbackError=false }) { Text(stringResource(R.string.close)) } })
}
