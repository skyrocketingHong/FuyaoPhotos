package ing.fuyaoskyrocket.photoinfo.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.layout.PreviewViewport
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.*
import ing.fuyaoskyrocket.photoinfo.platform.MotionClipSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import ing.fuyaoskyrocket.photoinfo.presentation.PhotoFailureMessages
import ing.fuyaoskyrocket.photoinfo.presentation.PhotoOperation
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PhotoPreview(bitmap:Bitmap?,modifier:Modifier=Modifier,backgroundColor:Color=Color(0xFF111315)) {
    Box(modifier.background(backgroundColor),contentAlignment=Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(),stringResource(R.string.preview_content),Modifier.fillMaxSize(),contentScale=ContentScale.Fit,alignment=Alignment.TopCenter) }
    }
}

@Composable
fun FullScreenPreview(bitmap:Bitmap,photoId:String,loadFullResolution:suspend ()->Bitmap,motion:MotionClipSource?,
    showHdr:Boolean,hdrEnabled:Boolean,hdrAvailable:Boolean,onHdr:()->Unit,onDismiss:()->Unit) {
    var playing by remember(photoId) { mutableStateOf(false) }
    var playbackError by remember(photoId) { mutableStateOf(false) }
    val context=LocalContext.current
    val currentLoader by rememberUpdatedState(loadFullResolution)
    var detail by remember(photoId,bitmap) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(photoId,bitmap) { mutableStateOf(true) }
    var failure by remember(photoId,bitmap) { mutableStateOf<String?>(null) }
    var retry by remember(photoId,bitmap) { mutableIntStateOf(0) }
    LaunchedEffect(photoId,bitmap,retry) {
        var pending:Bitmap?=null
        loading=true;failure=null
        try {
            val result=currentLoader().also { pending=it }
            currentCoroutineContext().ensureActive()
            detail=result;pending=null
        } catch(cancelled:CancellationException) { throw cancelled }
        catch(error:Exception) { failure=PhotoFailureMessages.describe(context,error,PhotoOperation.PREVIEW) }
        catch(error:OutOfMemoryError) { failure=PhotoFailureMessages.describe(context,error,PhotoOperation.PREVIEW) }
        finally { pending?.recycle();loading=false }
    }
    if(playbackError)AlertDialog(onDismissRequest={ playbackError=false },
        title={ Text(stringResource(R.string.motion_playback_title)) },text={ Text(stringResource(R.string.motion_playback_error)) },
        confirmButton={ TextButton(onClick={ playbackError=false }) { Text(stringResource(R.string.close)) } })
    // The published bitmap stays alive while Compose draws it; only unpublished results are recycled.
    val displayed=detail ?: bitmap
    val currentBitmap by rememberUpdatedState(displayed)
    failure?.let { message ->
        AlertDialog(onDismissRequest={ failure=null },
            title={ Text(stringResource(R.string.full_preview_error)) },text={ Text(message) },
            confirmButton={ TextButton(onClick={ retry++ }) { Text(stringResource(R.string.retry_preview)) } },
            dismissButton={ TextButton(onClick={ failure=null }) { Text(stringResource(R.string.keep_thumbnail_preview)) } })
    }
    var scale by remember(photoId) { mutableFloatStateOf(1f) }
    var offset by remember(photoId) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val scope=rememberCoroutineScope()
    var resetJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(photoId) { onDispose { resetJob?.cancel() } }
    fun bounded(value:Offset,zoom:Float):Offset {
        val pan=PreviewViewport.clamp(currentBitmap.width,currentBitmap.height,viewport.width,viewport.height,zoom,value.x,value.y)
        return Offset(pan.x,pan.y)
    }
    fun moveTo(target:Float) {
        resetJob?.cancel()
        val startScale=scale;val startOffset=offset
        val next=target.coerceIn(1f,8f)
        resetJob=scope.launch {
            // Compose's animation context honors the system animator-duration scale.
            animate(0f,1f,animationSpec=tween(FuyaoMotion.resetMillis,easing=FuyaoMotion.standard)) { fraction,_ ->
                scale=startScale+(next-startScale)*fraction
                offset=bounded(startOffset*(1-fraction),scale)
            }
        }
    }
    val window = LocalActivity.current?.window
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars ?: false
        val lightNavigation = controller?.isAppearanceLightNavigationBars ?: false
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = lightStatus
            controller?.isAppearanceLightNavigationBars = lightNavigation
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { viewport=it }
            .pointerInput(photoId) {
                detectTransformGestures { centroid,pan,zoom,_ ->
                    if(playing)return@detectTransformGestures
                    resetJob?.cancel()
                    val old=scale;val next=(old*zoom).coerceIn(1f,8f)
                    val center=Offset(viewport.width/2f,viewport.height/2f)
                    val anchored=(offset-(centroid-center))*(next/old)+(centroid-center)+pan
                    scale=next;offset=bounded(anchored,next)
                }
            }.pointerInput(photoId) { detectTapGestures(onDoubleTap={ if(!playing)moveTo(1f) }) }) {
            val zoomDescription=stringResource(R.string.zoom_value,(scale*100).roundToInt())
            Image(displayed.asImageBitmap(),stringResource(R.string.preview_content),Modifier.fillMaxSize()
                .semantics { stateDescription=zoomDescription }
                .graphicsLayer { scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y },contentScale=ContentScale.Fit)
            if(playing && motion!=null)MotionPhotoPreview(motion,Modifier.fillMaxSize(),
                onFinished={ playing=false },onError={ playing=false;playbackError=true })
            Surface(Modifier.align(Alignment.TopCenter),
                color=Color.Black.copy(alpha=.72f),contentColor=Color.White) {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal)).heightIn(min=48.dp).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    FuyaoIconButton(R.drawable.ic_close,stringResource(R.string.close),onDismiss)
                    Text(if(loading)stringResource(R.string.full_preview_loading) else stringResource(R.string.preview_quality,
                        zoomDescription,stringResource(if(detail!=null)R.string.original_size_preview else R.string.thumbnail_preview)),
                        Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,maxLines=2)
                    if(motion!=null)PreviewMediaButton(if(playing)R.drawable.ic_stop else R.drawable.ic_motion,
                        stringResource(if(playing)R.string.stop_motion else R.string.play_motion),playing,{ playing=!playing },
                        enabled=!loading)
                    if(showHdr)PreviewMediaButton(R.drawable.ic_hdr,
                        stringResource(if(!hdrAvailable)R.string.hdr_unavailable else if(hdrEnabled)R.string.disable_hdr else R.string.enable_hdr),
                        hdrEnabled && hdrAvailable,{ playing=false;onHdr() },enabled=hdrAvailable)

                }
            }
            Surface(Modifier.align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom+WindowInsetsSides.Horizontal)).padding(8.dp),
                color=Color.Black.copy(alpha=.72f),contentColor=Color.White,shape=MaterialTheme.shapes.large) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    FuyaoIconButton(R.drawable.ic_minus,stringResource(R.string.zoom_out),{ moveTo(scale/1.5f) },enabled=!playing && scale>1f)
                    FuyaoIconButton(R.drawable.ic_fit,stringResource(R.string.reset_zoom),{ moveTo(1f) },enabled=!playing)
                    FuyaoIconButton(R.drawable.ic_plus,stringResource(R.string.zoom_in),{ moveTo(scale*1.5f) },enabled=!playing && scale<8f)
                }
            }
        }
    }
}
