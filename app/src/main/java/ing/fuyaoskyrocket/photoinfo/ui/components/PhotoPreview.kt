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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PhotoPreview(bitmap:Bitmap?,modifier:Modifier=Modifier) {
    Box(modifier.background(Color(0xFF111315)),contentAlignment=Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(),stringResource(R.string.preview_content),Modifier.fillMaxSize(),contentScale=ContentScale.Fit) }
    }
}

@Composable
fun FullScreenPreview(bitmap:Bitmap,onDismiss:()->Unit) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val scope=rememberCoroutineScope()
    var resetJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(bitmap) { onDispose { resetJob?.cancel() } }
    fun bounded(value:Offset,zoom:Float):Offset {
        val pan=PreviewViewport.clamp(bitmap.width,bitmap.height,viewport.width,viewport.height,zoom,value.x,value.y)
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
            .pointerInput(bitmap) {
                detectTransformGestures { centroid,pan,zoom,_ ->
                    resetJob?.cancel()
                    val old=scale;val next=(old*zoom).coerceIn(1f,8f)
                    val center=Offset(viewport.width/2f,viewport.height/2f)
                    val anchored=(offset-(centroid-center))*(next/old)+(centroid-center)+pan
                    scale=next;offset=bounded(anchored,next)
                }
            }.pointerInput(bitmap) { detectTapGestures(onDoubleTap={ moveTo(1f) }) }) {
            val zoomDescription=stringResource(R.string.zoom_value,(scale*100).roundToInt())
            Image(bitmap.asImageBitmap(),stringResource(R.string.preview_content),Modifier.fillMaxSize()
                .semantics { stateDescription=zoomDescription }
                .graphicsLayer { scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y },contentScale=ContentScale.Fit)
            Surface(Modifier.align(Alignment.TopCenter),
                color=Color.Black.copy(alpha=.72f),contentColor=Color.White) {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal)).heightIn(min=48.dp).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    FuyaoIconButton(R.drawable.ic_close,stringResource(R.string.close),onDismiss)
                    Text(zoomDescription,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                    FuyaoIconButton(R.drawable.ic_minus,stringResource(R.string.zoom_out),{ moveTo(scale/1.5f) },enabled=scale>1f)
                    FuyaoIconButton(R.drawable.ic_fit,stringResource(R.string.reset_zoom),{ moveTo(1f) })
                    FuyaoIconButton(R.drawable.ic_plus,stringResource(R.string.zoom_in),{ moveTo(scale*1.5f) },enabled=scale<8f)
                }
            }
        }
    }
}
