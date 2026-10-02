package ing.fuyaoskyrocket.photoinfo.ui.designsystem

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import ing.fuyaoskyrocket.photoinfo.domain.layout.PageGeometry

@Composable
fun FuyaoScrollEdge(modifier: Modifier, topInset: Dp, scrollOffset: () -> Float,
    content: @Composable () -> Unit) {
    val source = rememberGraphicsLayer()
    val blurLayers = listOf(rememberGraphicsLayer(), rememberGraphicsLayer(), rememberGraphicsLayer())
    val masks = listOf(rememberGraphicsLayer(), rememberGraphicsLayer(), rememberGraphicsLayer())
    Box(modifier.drawWithContent {
        val progress = PageGeometry.scrollEdgeProgress(scrollOffset() / density)
        if (progress == 0f) {
            drawContent()
            return@drawWithContent
        }
        val height = minOf(size.height, topInset.toPx() + 32.dp.toPx())
        if (Build.VERSION.SDK_INT >= 31) {
            source.record { this@drawWithContent.drawContent() }
            drawLayer(source)
            blurLayers.forEachIndexed { index, layer ->
                val radius = (4 + index * 8).dp.toPx()
                val extent = height * (1f - index * .2f)
                val layerSize = IntSize(ceil(size.width).toInt(), ceil(height + radius * 2).toInt())
                layer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                layer.record(size = layerSize) { drawLayer(source) }
                val mask = masks[index]
                mask.compositingStrategy = CompositingStrategy.Offscreen
                mask.alpha = progress
                mask.record(size = layerSize) {
                    drawLayer(layer)
                    drawRect(Brush.verticalGradient(0f to Color.White, 1f to Color.Transparent,
                        endY = extent), blendMode = BlendMode.DstIn)
                }
                drawLayer(mask)
            }
        } else {
            // RenderEffect is unavailable before API 31; retain the unobscured content.
            drawContent()
        }
    }) { content() }
}
