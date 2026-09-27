package ing.fuyaoskyrocket.photoinfo.features.colors.ui.photo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.SampledColor

/**
 * Draws the droplet marker at the sampling point. The magnifier lives in the
 * result panel so it never obscures the image or jumps between corners.
 */
@Composable
internal fun SamplingIndicator(
    sampledColor: SampledColor,
    layout: ImageLayout,
) {
    val markerColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxSize()) {
        val tap = bitmapToViewport(
            Offset(sampledColor.sourceX + 0.5f, sampledColor.sourceY + 0.5f),
            layout,
        )
        // The halo remains visible outside the typical finger contact area.
        val haloRadius = 30.dp.toPx()
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = haloRadius,
            center = tap,
            style = Stroke(width = 4.dp.toPx()),
        )
        drawCircle(
            color = markerColor,
            radius = haloRadius,
            center = tap,
            style = Stroke(width = 2.dp.toPx()),
        )

        // --- Droplet marker ---
        val markerRadius = 18.dp.toPx()
        val markerOffset = 46.dp.toPx()
        val spaceAbove = tap.y
        val spaceBelow = size.height - tap.y
        val markerDirection = if (
            spaceAbove >= markerOffset + markerRadius || spaceAbove >= spaceBelow
        ) {
            -1f
        } else {
            1f
        }
        val markerEdgeInset = markerRadius + 4.dp.toPx()
        val markerMinY = markerEdgeInset.coerceAtMost(size.height / 2f)
        val markerMaxY = (size.height - markerEdgeInset).coerceAtLeast(size.height / 2f)
        val markerCenter = Offset(
            x = tap.x,
            y = (tap.y + markerDirection * markerOffset).coerceIn(
                markerMinY,
                markerMaxY,
            ),
        )
        val actualMarkerDirection = if (markerCenter.y < tap.y) -1f else 1f
        val tailBaseY = markerCenter.y - actualMarkerDirection * markerRadius * 0.5f
        val tail = Path().apply {
            moveTo(tap.x, tap.y)
            lineTo(markerCenter.x - markerRadius * 0.62f, tailBaseY)
            lineTo(markerCenter.x + markerRadius * 0.62f, tailBaseY)
            close()
        }
        drawPath(path = tail, color = markerColor)
        drawCircle(color = markerColor, radius = markerRadius, center = markerCenter)
        drawCircle(
            color = Color.White, radius = markerRadius, center = markerCenter,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
            color = Color(sampledColor.sRgb.argb),
            radius = markerRadius * 0.48f,
            center = markerCenter,
        )
    }
}
