package ing.fuyaoskyrocket.photoinfo.features.colors.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.SampledColor
import kotlin.math.roundToInt

/** Fixed result-panel magnifier that never obscures the source image. */
@Composable
internal fun SamplingMagnifier(
    bitmap: Bitmap?,
    sampledColor: SampledColor?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val outlineColor = MaterialTheme.colorScheme.outline
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        val diameter = size.minDimension
        if (diameter <= 0f) return@Canvas
        val radius = diameter / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val bounds = Rect(
            left = center.x - radius,
            top = center.y - radius,
            right = center.x + radius,
            bottom = center.y + radius,
        )
        val lensPath = Path().apply { addOval(bounds) }

        drawCircle(color = emptyColor, radius = radius, center = center)
        if (bitmap != null && imageBitmap != null && sampledColor != null) {
            val sourceSize = minOf(11, bitmap.width, bitmap.height)
            val sourceLeft = (sampledColor.sourceX - sourceSize / 2)
                .coerceIn(0, bitmap.width - sourceSize)
            val sourceTop = (sampledColor.sourceY - sourceSize / 2)
                .coerceIn(0, bitmap.height - sourceSize)
            val destinationSize = diameter.roundToInt()

            clipPath(lensPath) {
                drawImage(
                    image = imageBitmap,
                    srcOffset = IntOffset(sourceLeft, sourceTop),
                    srcSize = IntSize(sourceSize, sourceSize),
                    dstOffset = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()),
                    dstSize = IntSize(destinationSize, destinationSize),
                    filterQuality = FilterQuality.None,
                )
                val cellSize = diameter / sourceSize
                for (index in 0..sourceSize) {
                    val gridX = bounds.left + index * cellSize
                    val gridY = bounds.top + index * cellSize
                    drawLine(
                        color = Color.Black.copy(alpha = 0.18f),
                        start = Offset(gridX, bounds.top),
                        end = Offset(gridX, bounds.bottom),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        color = Color.Black.copy(alpha = 0.18f),
                        start = Offset(bounds.left, gridY),
                        end = Offset(bounds.right, gridY),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }

            val focalPoint = Offset(
                x = bounds.left +
                    (sampledColor.sourceX - sourceLeft + 0.5f) * diameter / sourceSize,
                y = bounds.top +
                    (sampledColor.sourceY - sourceTop + 0.5f) * diameter / sourceSize,
            )
            drawCrosshair(
                focalPoint = focalPoint,
                bounds = bounds,
                backgroundColor = Color.Black.copy(alpha = 0.62f),
                foregroundColor = Color.White,
            )
        } else {
            drawCrosshair(
                focalPoint = center,
                bounds = bounds,
                backgroundColor = Color.Transparent,
                foregroundColor = outlineColor,
            )
        }

        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = (radius - 1.dp.toPx()).coerceAtLeast(0f),
            center = center,
            style = Stroke(width = 3.dp.toPx()),
        )
        drawCircle(
            color = if (sampledColor == null) outlineColor else primaryColor,
            radius = (radius - 0.5.dp.toPx()).coerceAtLeast(0f),
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrosshair(
    focalPoint: Offset,
    bounds: Rect,
    backgroundColor: Color,
    foregroundColor: Color,
) {
    val arm = 11.dp.toPx()
    val ringRadius = 8.dp.toPx()
    if (backgroundColor != Color.Transparent) {
        drawLine(
            color = backgroundColor,
            start = Offset((focalPoint.x - arm).coerceAtLeast(bounds.left), focalPoint.y),
            end = Offset((focalPoint.x + arm).coerceAtMost(bounds.right), focalPoint.y),
            strokeWidth = 4.dp.toPx(),
        )
        drawLine(
            color = backgroundColor,
            start = Offset(focalPoint.x, (focalPoint.y - arm).coerceAtLeast(bounds.top)),
            end = Offset(focalPoint.x, (focalPoint.y + arm).coerceAtMost(bounds.bottom)),
            strokeWidth = 4.dp.toPx(),
        )
        drawCircle(
            color = backgroundColor,
            radius = ringRadius,
            center = focalPoint,
            style = Stroke(width = 4.dp.toPx()),
        )
    }
    drawLine(
        color = foregroundColor,
        start = Offset((focalPoint.x - arm).coerceAtLeast(bounds.left), focalPoint.y),
        end = Offset((focalPoint.x + arm).coerceAtMost(bounds.right), focalPoint.y),
        strokeWidth = 1.5.dp.toPx(),
    )
    drawLine(
        color = foregroundColor,
        start = Offset(focalPoint.x, (focalPoint.y - arm).coerceAtLeast(bounds.top)),
        end = Offset(focalPoint.x, (focalPoint.y + arm).coerceAtMost(bounds.bottom)),
        strokeWidth = 1.5.dp.toPx(),
    )
    drawCircle(
        color = foregroundColor,
        radius = ringRadius,
        center = focalPoint,
        style = Stroke(width = 1.5.dp.toPx()),
    )
}
