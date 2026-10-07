package ing.fuyaoskyrocket.photoinfo.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.domain.render.BoxBlur
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun PhotoAmbientBackdrop(bitmap: Bitmap?, featherEdges: Boolean = true, modifier: Modifier = Modifier) {
    if (bitmap == null) return
    val scaled = remember(bitmap) {
        val largest = max(bitmap.width, bitmap.height)
        val limit = if (Build.VERSION.SDK_INT >= 31) 700f else 320f
        val factor = minOf(1f, limit / largest)
        val width = (bitmap.width * factor).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * factor).roundToInt().coerceAtLeast(1)
        // Keep HDR and gain maps on the photo; the ambient copy is a small SDR surface.
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { target ->
            Canvas(target).drawBitmap(bitmap, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
            // Compose's blur modifier needs API 31; older devices use the existing CPU blur.
            if (Build.VERSION.SDK_INT < 31) {
                val pixels = IntArray(width * height)
                target.getPixels(pixels, 0, width, 0, 0, width, height)
                val blurred = BoxBlur.blur(pixels, width, height, (max(width, height) * .05f).roundToInt().coerceAtLeast(1))
                target.setPixels(blurred, 0, width, 0, 0, width, height)
            }
        }
    }
    val surface = MaterialTheme.colorScheme.surface
    Box(modifier.navigationBackdropSource(priority = 0).clipToBounds()
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (featherEdges) drawRect(Brush.horizontalGradient(
                0f to Color.Transparent, .18f to Color.White,
                .82f to Color.White, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            drawRect(Brush.verticalGradient(
                0f to (if (featherEdges) Color.Transparent else Color.White),
                .16f to Color.White, .4f to Color.White, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
        }) {
        Image(scaled.asImageBitmap(), null, Modifier.fillMaxSize().blur(44.dp),
            contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(surface.copy(alpha = if (surface.luminance() < .5f) .80f else .88f)))
    }
}
