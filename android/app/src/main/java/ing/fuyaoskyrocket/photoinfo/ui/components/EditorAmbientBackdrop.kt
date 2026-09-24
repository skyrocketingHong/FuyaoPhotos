package ing.fuyaoskyrocket.photoinfo.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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

@Composable
internal fun EditorAmbientBackdrop(bitmap: Bitmap?, modifier: Modifier = Modifier) {
    if (bitmap == null) return
    Box(modifier.clipToBounds()
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(Brush.horizontalGradient(
                0f to Color.Transparent, .12f to Color.White,
                .88f to Color.White, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            drawRect(Brush.verticalGradient(
                0f to Color.White, .58f to Color.White,
                1f to Color.Transparent), blendMode = BlendMode.DstIn)
        }) {
        Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().blur(20.dp),
            contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f)))
    }
}
