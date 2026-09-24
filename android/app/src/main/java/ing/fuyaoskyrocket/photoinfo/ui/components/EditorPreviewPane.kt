package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.presentation.EditorState
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoIconButton
import ing.fuyaoskyrocket.photoinfo.platform.MotionClipSource
import kotlinx.coroutines.launch

@Composable
fun EditorPreviewPane(state:EditorState,onSelectPhoto:(Int)->Unit,original:Boolean,onOriginal:()->Unit,
    onEnlarge:()->Unit,motion:MotionClipSource?,showHdr:Boolean,hdrEnabled:Boolean,hdrAvailable:Boolean,onHdr:()->Unit,
    modifier:Modifier=Modifier,bottomSafe:Boolean=false,actions:@Composable RowScope.()->Unit={}) {
    val photoId=state.photos.getOrNull(state.photoIndex)?.id
    var playing by remember(photoId) { mutableStateOf(false) }
    var playbackError by remember(photoId) { mutableStateOf(false) }
    var showingInfo by remember(photoId) { mutableStateOf(false) }
    LaunchedEffect(state.busy,state.rendering,motion) { if(state.busy || state.rendering || motion==null)playing=false }
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val selectedIndex by rememberUpdatedState(state.photoIndex)
    val scope=rememberCoroutineScope()
    val previous=stringResource(R.string.previous_photo)
    val next=stringResource(R.string.next_photo)
    val position=stringResource(R.string.photo_position,state.photoIndex+1,state.photos.size)
    val details=state.previewError ?: state.mediaMessage?.let { stringResource(it) }
    val info=details ?: stringResource(R.string.photo_dimensions,state.width,state.height)
    BoxWithConstraints(modifier.then(if(bottomSafe)Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier)) {
    val showPhoto=maxHeight>60.dp
    LaunchedEffect(showPhoto) { if(!showPhoto)playing=false }
    Column(Modifier.fillMaxSize()) {
        if(showPhoto) {
        Surface(Modifier.fillMaxWidth().weight(1f).padding(horizontal=8.dp,vertical=4.dp),
            color=MaterialTheme.colorScheme.surfaceContainerLow,tonalElevation=1.dp,shadowElevation=3.dp) {
            key(state.sessionId) {
                val pager=rememberPagerState(initialPage=state.photoIndex) { state.photos.size }
                LaunchedEffect(pager.isScrollInProgress) { if(pager.isScrollInProgress)playing=false }
                LaunchedEffect(pager) {
                    snapshotFlow { pager.settledPage }.collect { page ->
                        if(page!=selectedIndex) { focus.clearFocus(force=true);keyboard?.hide();onSelectPhoto(page) }
                    }
                }
                HorizontalPager(state=pager,key={ state.photos[it].id },modifier=Modifier.fillMaxSize().semantics {
                    stateDescription=position
                    customActions=buildList {
                        if(!state.busy && state.photoIndex>0)add(CustomAccessibilityAction(previous) { scope.launch { pager.scrollToPage(state.photoIndex-1) };true })
                        if(!state.busy && state.photoIndex<state.photos.lastIndex)add(CustomAccessibilityAction(next) { scope.launch { pager.scrollToPage(state.photoIndex+1) };true })
                    }
                },userScrollEnabled=(!state.busy || state.loadingPhoto) && !state.closing) { page ->
                    if(page==state.photoIndex)Box(Modifier.fillMaxSize()) {
                        PhotoPreview(if(original)state.original else state.preview,Modifier.fillMaxSize())
                        if(playing && motion!=null)MotionPhotoPreview(motion,Modifier.fillMaxSize(),
                            onFinished={ playing=false },onError={ playing=false;playbackError=true })
                    } else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
        }
        Surface(color=MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().height(52.dp).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                if(state.motionPhoto)PreviewMediaButton(if(playing)R.drawable.ic_stop else R.drawable.ic_motion,
                    stringResource(if(playing)R.string.stop_motion else R.string.play_motion),playing,{ playing=!playing },
                    enabled=motion!=null && !state.busy && !state.rendering)
                if(showHdr)PreviewMediaButton(R.drawable.ic_hdr,
                    stringResource(if(!hdrAvailable)R.string.hdr_unavailable else if(hdrEnabled)R.string.disable_hdr else R.string.enable_hdr),
                    hdrEnabled && hdrAvailable,{ playing=false;onHdr() },enabled=hdrAvailable)
                PreviewMediaButton(R.drawable.ic_compare,stringResource(R.string.original),original,{ playing=false;onOriginal() },enabled=state.original!=null)
                actions()
                FuyaoIconButton(R.drawable.ic_expand,stringResource(R.string.enlarge),{ playing=false;onEnlarge() },enabled=state.preview!=null)
                BadgedBox(badge={ if(state.preservationBlocked || state.previewError!=null)Badge() }) {
                    FuyaoIconButton(R.drawable.ic_info,stringResource(R.string.photo_details),{ showingInfo=true })
                }
                if(state.busy || state.rendering)CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp),strokeWidth=2.dp)
            }
        }
    }
    }
    if(showingInfo)AlertDialog(onDismissRequest={ showingInfo=false },title={ Text(stringResource(R.string.photo_details)) },
        text={ Text("$position\n$info") },confirmButton={ TextButton(onClick={ showingInfo=false }) { Text(stringResource(R.string.close)) } })
    if(playbackError)AlertDialog(onDismissRequest={ playbackError=false },title={ Text(stringResource(R.string.motion_playback_title)) },
        text={ Text(stringResource(R.string.motion_playback_error)) },confirmButton={ TextButton(onClick={ playbackError=false }) { Text(stringResource(R.string.close)) } })
}
