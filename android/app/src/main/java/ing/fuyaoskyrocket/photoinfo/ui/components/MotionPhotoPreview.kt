package ing.fuyaoskyrocket.photoinfo.ui.components

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ing.fuyaoskyrocket.photoinfo.platform.MotionClipPlayer
import ing.fuyaoskyrocket.photoinfo.platform.MotionClipSource
import ing.fuyaoskyrocket.photoinfo.R

@Composable
fun MotionPhotoPreview(source: MotionClipSource, modifier: Modifier = Modifier,
    onFinished: () -> Unit, onError: () -> Unit) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val finished by rememberUpdatedState(onFinished)
    val failed by rememberUpdatedState(onError)
    var ratio by remember(source) { mutableFloatStateOf(4f/3f) }
    var loading by remember(source) { mutableStateOf(true) }
    val player=remember(source) { MotionClipPlayer(context,
        onSize={ width,height -> ratio=width.toFloat()/height },onReady={ loading=false },
        onFinished={ finished() },onError={ failed() }) }
    DisposableEffect(player,owner) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_PAUSE || event==Lifecycle.Event.ON_STOP) { player.release();finished() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer);player.release() }
    }
    BoxWithConstraints(modifier.background(Color.Black),contentAlignment=Alignment.Center) {
        val width=minOf(maxWidth,maxHeight*ratio)
        AndroidView(factory={ ctx -> SurfaceView(ctx).apply {
            holder.addCallback(object: SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) { player.prepare(source,holder) }
                override fun surfaceChanged(holder: SurfaceHolder,format:Int,width:Int,height:Int) = Unit
                override fun surfaceDestroyed(holder: SurfaceHolder) { player.release();finished() }
            })
        } },modifier=Modifier.width(width).height(width/ratio),onReset=null,onRelease={ player.release() })
        if(loading)Icon(painterResource(R.drawable.ic_motion),stringResource(R.string.loading_motion),
            Modifier.size(24.dp),tint=Color.White)
    }
}
