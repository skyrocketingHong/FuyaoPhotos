package ing.fuyaoskyrocket.photoinfo.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/** Mirrors the iOS photo ambient backdrop: low-resolution blur dimmed toward the bottom. */
@Composable
internal fun EditorAmbientBackdrop(bitmap: Bitmap?, featherEdges: Boolean = true, modifier: Modifier = Modifier) {
    if (bitmap == null) return
    val scaled = remember(bitmap) {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= 700) null
        else {
            val factor = 700f / largest
            Bitmap.createScaledBitmap(bitmap,
                (bitmap.width * factor).roundToInt().coerceAtLeast(1),
                (bitmap.height * factor).roundToInt().coerceAtLeast(1), true)
        }
    }
    DisposableEffect(bitmap) { onDispose { scaled?.recycle() } }
    val source = scaled ?: bitmap
    Box(modifier.clipToBounds()
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (featherEdges) drawRect(Brush.horizontalGradient(
                0f to Color.Transparent, .18f to Color.White,
                .82f to Color.White, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            drawRect(Brush.verticalGradient(
                0f to Color.White, .38f to Color.White, .64f to Color.Transparent), blendMode = BlendMode.DstIn)
        }) {
        Image(source.asImageBitmap(), null, Modifier.fillMaxSize().blur(44.dp),
            contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .56f)))
    }
}
