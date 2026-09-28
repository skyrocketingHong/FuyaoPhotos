package ing.fuyaoskyrocket.photoinfo.features.colors.ui.photo

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt

internal data class LoupePlacement(val center: Offset, val diameter: Float, val belowFinger: Boolean)

internal fun placeColorLoupe(finger: Offset, panel: Size, density: Float): LoupePlacement {
    val inset = min(8f * density, min(panel.width, panel.height).coerceAtLeast(0f) / 4f)
    val diameter = min(100f * density, min(panel.width, panel.height) - inset * 2).coerceAtLeast(0f)
    val radius = diameter / 2
    val below = finger.y - 78f * density - radius < inset
    return LoupePlacement(Offset(
        finger.x.coerceIn(radius + inset, (panel.width - radius - inset).coerceAtLeast(radius + inset)),
        (finger.y + (if (below) 78 else -78) * density).coerceIn(radius + inset,
            (panel.height - radius - inset).coerceAtLeast(radius + inset))), diameter, below)
}

@Composable
internal fun ColorLoupe(bitmap: Bitmap, layout: ImageLayout, finger: Offset, visible: Boolean, hex: String?) {
    val density = LocalDensity.current
    val placement = placeColorLoupe(finger, Size(layout.viewportWidth, layout.viewportHeight), density.density)
    val fraction by animateFloatAsState(if (visible) 1f else 0f,
        spring(dampingRatio = 0.7f, stiffness = if (visible) 440f else 195f), label = "colorLoupe")
    val renderer = remember(bitmap) {
        if (Build.VERSION.SDK_INT >= 33) RefractingColorLens(bitmap) else PlainColorLens(bitmap)
    }
    val samplingPoint = samplingPointAtFinger(finger, layout, 72f * density.density) ?: return
    val pixel = viewportToBitmapForSampling(samplingPoint, layout) ?: return
    val source = Offset(pixel.x.roundToInt() + 0.5f, pixel.y.roundToInt() + 0.5f)
    Box(Modifier
        .offset { IntOffset((placement.center.x - placement.diameter / 2).roundToInt(),
            (placement.center.y - placement.diameter / 2).roundToInt()) }
        .size(with(density) { placement.diameter.toDp() })
        .graphicsLayer {
            alpha = fraction.coerceIn(0f, 1f)
            scaleX = 0.4f + fraction * 0.6f; scaleY = scaleX
            transformOrigin = TransformOrigin(0.5f, if (placement.belowFinger) 0f else 1f)
        }
        .shadow(8.dp, CircleShape, clip = false)
        .clearAndSetSemantics {}) {
        Canvas(Modifier.matchParentSize()) {
            drawIntoCanvas { renderer.draw(it.nativeCanvas, size.width, source, layout.renderScale) }
            drawCircle(Color.White.copy(alpha = 0.95f), radius = size.width / 2 - 1.25.dp.toPx(), style = Stroke(2.5.dp.toPx()))
            val center = Offset(size.width / 2, size.height / 2)
            for (stroke in listOf(Color.Black to 3.dp.toPx(), Color.White to 1.5.dp.toPx())) {
                drawLine(stroke.first, center - Offset(6.dp.toPx(), 0f), center + Offset(6.dp.toPx(), 0f), stroke.second)
                drawLine(stroke.first, center - Offset(0f, 6.dp.toPx()), center + Offset(0f, 6.dp.toPx()), stroke.second)
            }
        }
        if (hex != null) Text(hex, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f), CircleShape)
                .padding(horizontal = 5.dp, vertical = 2.dp))
    }
}

private interface ColorLensRenderer {
    fun draw(canvas: AndroidCanvas, diameter: Float, source: Offset, renderScale: Float)
}

private class PlainColorLens(bitmap: Bitmap) : ColorLensRenderer {
    private val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = this@PlainColorLens.shader }
    private val matrix = Matrix()
    override fun draw(canvas: AndroidCanvas, diameter: Float, source: Offset, renderScale: Float) {
        val scale = renderScale * 3
        matrix.setScale(scale, scale)
        matrix.postTranslate(diameter / 2 - source.x * scale, diameter / 2 - source.y * scale)
        shader.setLocalMatrix(matrix)
        canvas.drawCircle(diameter / 2, diameter / 2, diameter / 2, paint)
    }
}

@RequiresApi(33)
private class RefractingColorLens(bitmap: Bitmap) : ColorLensRenderer {
    private val shader = RuntimeShader(COLOR_LENS_SHADER).apply {
        setInputShader("photo", BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = this@RefractingColorLens.shader }
    override fun draw(canvas: AndroidCanvas, diameter: Float, source: Offset, renderScale: Float) {
        shader.setFloatUniform("diameter", diameter)
        shader.setFloatUniform("source", source.x, source.y)
        shader.setFloatUniform("sampleScale", 1f / (renderScale * 1.5f))
        canvas.drawCircle(diameter / 2, diameter / 2, diameter / 2, paint)
    }
}

// Adapted from Motionary's spherical lens; see LICENSES/Motionary-MIT.txt.
private const val COLOR_LENS_SHADER = """
uniform shader photo;
uniform float diameter;
uniform float2 source;
uniform float sampleScale;
half4 main(float2 position) {
    float radius = max(diameter * 0.5, 1.0);
    float2 d = position - float2(radius);
    float distance = length(d);
    float t = min(distance / radius, 1.0);
    float z = sqrt(max(1.0 - t * t, 0.0));
    float2 direction = distance > 0.001 ? d / distance : float2(0.0);
    float core = distance * mix(0.5, 1.0, t * t);
    float rim = (1.0 - z) * radius * 0.28;
    float spread = 0.12 * (1.0 - z);
    half4 g = photo.eval(source + direction * (core - rim) * sampleScale);
    half4 r = photo.eval(source + direction * (core - rim * (1.0 - spread)) * sampleScale);
    half4 b = photo.eval(source + direction * (core - rim * (1.0 + spread)) * sampleScale);
    half4 lens = half4(r.r, g.g, b.b, max(g.a, max(r.a, b.a)));
    float3 normal = normalize(float3(d / radius, z));
    float specular = pow(max(dot(normal, normalize(float3(-0.45, -0.6, 0.66))), 0.0), 36.0);
    lens.rgb = lens.rgb * half(0.9 + 0.1 * z) + half(specular * 0.5) * lens.a;
    return lens;
}
"""
