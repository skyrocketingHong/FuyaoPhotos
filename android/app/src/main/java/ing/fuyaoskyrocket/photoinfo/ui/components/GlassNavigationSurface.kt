package ing.fuyaoskyrocket.photoinfo.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.ui.theme.LocalPhotoMotionEnabled
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun GlassNavigationSurface(
    bitmap: Bitmap?, selected: Int, itemCount: Int,
    content: @Composable (iconModifier: (Int) -> Modifier) -> Unit,
) {
    val motion = LocalPhotoMotionEnabled.current
    val surface = MaterialTheme.colorScheme.surface
    val indicator = MaterialTheme.colorScheme.primaryContainer
    val outline = MaterialTheme.colorScheme.outlineVariant
    val shape = RoundedCornerShape(28.dp)
    val backdrop = rememberGraphicsLayer()
    val centers = remember { mutableStateMapOf<Int, Offset>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val center = centers[selected]?.minus(origin)
    val x = if (center != null) animateFloatAsState(center.x,
        if (motion) spring(1f, 300f) else snap(), label = "navigation selection") else null
    val image = remember(bitmap) {
        bitmap?.let {
            val factor = minOf(1f, 256f / max(it.width, it.height))
            val width = (it.width * factor).roundToInt().coerceAtLeast(1)
            val height = (it.height * factor).roundToInt().coerceAtLeast(1)
            // The material owns an SDR copy; it never records the live HDR photo layer.
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { target ->
                AndroidCanvas(target).drawBitmap(it, null, AndroidRect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
            }.asImageBitmap()
        }
    }
    val lens = if (Build.VERSION.SDK_INT >= 33) remember {
        try { GlassLens() } catch (_: IllegalArgumentException) { null }
    } else null
    Box(Modifier.fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
        .padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(max = 600.dp).fillMaxWidth().clip(shape)
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }) {
            Box(Modifier.matchParentSize().drawWithContent {
                backdrop.renderEffect = if (Build.VERSION.SDK_INT >= 33 && lens != null) {
                    lens.effect(size.width, size.height, 28.dp.toPx(), if (motion) 5.dp.toPx() else 0f, 16.dp.toPx())
                } else if (Build.VERSION.SDK_INT >= 31) BlurEffect(16.dp.toPx(), 16.dp.toPx(), TileMode.Clamp) else null
                backdrop.record { this@drawWithContent.drawContent() }
                drawLayer(backdrop)
            }.background(surface)) {
                if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Canvas(Modifier.matchParentSize()) {
                // Keep text contrast close to the native surface even over bright or dark photos.
                drawRect(surface.copy(alpha = if (surface.luminance() < .5f) .94f else .92f))
                val radius = 28.dp.toPx()
                drawRoundRect(outline.copy(alpha = .55f), cornerRadius = CornerRadius(radius), style = Stroke(1.dp.toPx()))
                if (center != null) {
                    val width = minOf(64.dp.toPx(), size.width / itemCount * .86f)
                    val height = 34.dp.toPx()
                    val selectedX = x?.value ?: center.x
                    val pill = Rect(selectedX - width / 2, center.y - height / 2,
                        selectedX + width / 2, center.y + height / 2)
                    val mask = Path().apply { addRoundRect(RoundRect(pill, CornerRadius(height / 2))) }
                    clipPath(mask) {
                        scale(if (motion) 1.06f else 1f, pivot = Offset(selectedX, center.y)) { drawLayer(backdrop) }
                    }
                    drawRoundRect(indicator.copy(alpha = .9f), Offset(selectedX - width / 2, center.y - height / 2),
                        Size(width, height), CornerRadius(height / 2))
                    drawRoundRect(outline.copy(alpha = .35f), Offset(selectedX - width / 2, center.y - height / 2),
                        Size(width, height), CornerRadius(height / 2), style = Stroke(.5.dp.toPx()))
                }
            }
            content { index ->
                Modifier.onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    centers[index] = bounds.center
                }
            }
        }
    }
}

@RequiresApi(33)
private class GlassLens {
    private val shader = RuntimeShader("""
        uniform shader scene;
        uniform float2 extent;
        uniform float corner;
        uniform float bend;
        half4 main(float2 p) {
            float2 halfSize = extent * 0.5;
            float2 q = abs(p - halfSize) - (halfSize - corner);
            float distance = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - corner;
            float edge = 1.0 - smoothstep(0.0, corner, -distance);
            float2 direction = (p - halfSize) / max(halfSize, float2(1.0));
            float2 samplePoint = clamp(p - direction * bend * edge * edge, float2(0.0), extent);
            return scene.eval(samplePoint);
        }
    """.trimIndent())

    fun effect(width: Float, height: Float, radius: Float, bend: Float, blur: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("extent", width, height)
        shader.setFloatUniform("corner", radius)
        shader.setFloatUniform("bend", bend)
        return RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader, "scene"),
            RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)).asComposeRenderEffect()
    }
}
