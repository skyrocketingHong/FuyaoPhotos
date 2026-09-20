package ing.fuyaoskyrocket.photoinfo.ui.components

import android.graphics.Bitmap
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ing.fuyaoskyrocket.photoinfo.R

@Composable
fun PhotoPreview(bitmap: Bitmap?, modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0xFF111315)), contentAlignment = Alignment.Center) {
        if (bitmap == null) {
            Text(stringResource(R.string.empty_hint), modifier = Modifier.padding(28.dp), color = Color(0xFFC7C9CD))
        } else Image(
            bitmap = bitmap.asImageBitmap(), contentDescription = stringResource(R.string.preview_content),
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
        )
    }
}

@Composable
fun FullScreenPreview(bitmap: Bitmap, onDismiss: () -> Unit) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { viewport = it }
            .pointerInput(bitmap) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    val nextScale = (oldScale * zoom).coerceIn(1f, 8f)
                    val center = Offset(viewport.width / 2f, viewport.height / 2f)
                    val anchored = (offset - (centroid - center)) * (nextScale / oldScale) + (centroid - center) + pan
                    val fit = minOf(viewport.width.toFloat() / bitmap.width, viewport.height.toFloat() / bitmap.height)
                    val limitX = maxOf(0f, (bitmap.width * fit * nextScale - viewport.width) / 2f)
                    val limitY = maxOf(0f, (bitmap.height * fit * nextScale - viewport.height) / 2f)
                    scale = nextScale
                    offset = Offset(anchored.x.coerceIn(-limitX, limitX), anchored.y.coerceIn(-limitY, limitY))
                }
            }.pointerInput(bitmap) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) }) {
            Image(bitmap.asImageBitmap(), stringResource(R.string.preview_content),
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                }, contentScale = ContentScale.Fit)
            Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { scale = 1f; offset = Offset.Zero }) { Text(stringResource(R.string.reset_zoom)) }
                FilledTonalButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        }
    }
}
