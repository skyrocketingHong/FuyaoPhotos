package ing.fuyaoskyrocket.photoinfo.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

@Composable
internal fun CardDetailPreview(
    bitmap: Bitmap?,
    box: CardBox?,
    rendering: Boolean,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(if (errorMessage == null) R.string.preview_content else R.string.error_preview_title)
    Box(
        modifier.clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .semantics { contentDescription = errorMessage ?: label },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null && box != null) {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            val crop = remember(bitmap, box) { cardCrop(bitmap, box) }
            Canvas(Modifier.fillMaxSize()) {
                if (size.width <= 0f || size.height <= 0f) return@Canvas
                val scale = minOf(size.width / crop.width, size.height / crop.height)
                val width = (crop.width * scale).roundToInt().coerceAtLeast(1)
                val height = (crop.height * scale).roundToInt().coerceAtLeast(1)
                drawImage(
                    image = image,
                    srcOffset = IntOffset(crop.left, crop.top),
                    srcSize = IntSize(crop.width, crop.height),
                    dstOffset = IntOffset(((size.width - width) / 2f).roundToInt(), ((size.height - height) / 2f).roundToInt()),
                    dstSize = IntSize(width, height),
                    filterQuality = FilterQuality.High,
                )
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
        if (rendering) {
            CircularProgressIndicator(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp),
                strokeWidth = 2.dp,
            )
        }
    }
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
