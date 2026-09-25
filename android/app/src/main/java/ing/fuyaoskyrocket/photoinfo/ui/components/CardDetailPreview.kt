package ing.fuyaoskyrocket.photoinfo.ui.components

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.layout.CardBox
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private data class CardCrop(val left: Int, val top: Int, val width: Int, val height: Int)
internal enum class CardPreviewStyleHighlight { CARD, RIGHT, BOTTOM }

@Composable
internal fun CardDetailPreview(
    bitmap: Bitmap?,
    box: CardBox?,
    rendering: Boolean,
    errorMessage: String? = null,
    editingActive: Boolean = false,
    highlightRects: List<RectF> = emptyList(),
    highlightStyle: CardPreviewStyleHighlight? = null,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(if (errorMessage == null) R.string.preview_content else R.string.error_preview_title)
    val updating = stringResource(R.string.card_preview_updating)
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier.clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .semantics {
                contentDescription = errorMessage ?: label
                if (rendering || editingActive) stateDescription = updating
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null && box != null) {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            val crop = remember(bitmap, box) { cardCrop(bitmap, box) }
            val selectedRows = highlightRects.filter { it.width() > 0f && it.height() > 0f }
            val cardBoxRect = remember(box) { RectF(box.left, box.top, box.right, box.bottom) }
            val targets = when {
                selectedRows.isNotEmpty() -> selectedRows
                highlightStyle != null -> listOf(cardBoxRect)
                else -> emptyList()
            }
            val animatedRects = rememberAnimatedRects(targets)
            val fieldMode = selectedRows.isNotEmpty()
            PendingPhotoEffect(rendering || editingActive, Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    if (size.width <= 0f || size.height <= 0f) return@Canvas
                    val scale = minOf(size.width / crop.width, size.height / crop.height)
                    val width = (crop.width * scale).roundToInt().coerceAtLeast(1)
                    val height = (crop.height * scale).roundToInt().coerceAtLeast(1)
                    val left = ((size.width - width) / 2f).roundToInt()
                    val top = ((size.height - height) / 2f).roundToInt()
                    drawImage(
                        image = image,
                        srcOffset = IntOffset(crop.left, crop.top),
                        srcSize = IntSize(crop.width, crop.height),
                        dstOffset = IntOffset(left, top),
                        dstSize = IntSize(width, height),
                        filterQuality = FilterQuality.High,
                    )
                    val xScale = width.toFloat() / crop.width
                    val yScale = height.toFloat() / crop.height
                    fun map(rect: RectF) = RectF(
                        left + (rect.left - crop.left) * xScale,
                        top + (rect.top - crop.top) * yScale,
                        left + (rect.right - crop.left) * xScale,
                        top + (rect.bottom - crop.top) * yScale,
                    )
                    if (fieldMode) {
                        animatedRects.forEach { row ->
                            val rect = map(row)
                            val corner = CornerRadius(3.dp.toPx())
                            drawRoundRect(accent.copy(alpha = .18f), Offset(rect.left, rect.top),
                                Size(rect.width(), rect.height()), corner)
                            drawRoundRect(accent, Offset(rect.left, rect.top),
                                Size(rect.width(), rect.height()), corner,
                                style = Stroke(1.5.dp.toPx()))
                        }
                    } else if (highlightStyle != null && animatedRects.isNotEmpty()) {
                        val rect = map(animatedRects.first())
                        val stroke = 2.dp.toPx()
                        when (highlightStyle) {
                            CardPreviewStyleHighlight.CARD -> drawRoundRect(
                                accent, Offset(rect.left, rect.top), Size(rect.width(), rect.height()),
                                CornerRadius(8.dp.toPx()), style = Stroke(stroke))
                            CardPreviewStyleHighlight.RIGHT -> drawLine(accent,
                                Offset(rect.right, rect.top), Offset(rect.right, rect.bottom), stroke)
                            CardPreviewStyleHighlight.BOTTOM -> drawLine(accent,
                                Offset(rect.left, rect.bottom), Offset(rect.right, rect.bottom), stroke)
                        }
                    }
                }
            }
        } else {
            Text(
                label,
                Modifier.padding(12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Moves the highlight between selections with an eased, non-linear slide. */
@Composable
private fun rememberAnimatedRects(targets: List<RectF>): List<RectF> {
    val animated = remember { mutableStateOf(targets) }
    LaunchedEffect(targets) {
        val from = animated.value
        if (from == targets) return@LaunchedEffect
        val progress = Animatable(0f)
        progress.animateTo(1f, tween(durationMillis = 280, easing = CubicBezierEasing(.2f, 0f, 0f, 1f))) {
            animated.value = targets.mapIndexed { index, target ->
                val start = from.getOrNull(index) ?: target
                RectF(
                    start.left + (target.left - start.left) * value,
                    start.top + (target.top - start.top) * value,
                    start.right + (target.right - start.right) * value,
                    start.bottom + (target.bottom - start.bottom) * value,
                )
            }
        }
        animated.value = targets
    }
    return animated.value
}

private fun cardCrop(bitmap: Bitmap, box: CardBox): CardCrop {
    val horizontalContext = box.width * 0.08f
    val verticalContext = box.height * 0.08f
    val left = floor(box.left - horizontalContext).toInt().coerceIn(0, bitmap.width - 1)
    val top = floor(box.top - verticalContext).toInt().coerceIn(0, bitmap.height - 1)
    val right = ceil(box.right + horizontalContext).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = ceil(box.bottom + verticalContext).toInt().coerceIn(top + 1, bitmap.height)
    return CardCrop(left, top, right - left, bottom - top)
}
