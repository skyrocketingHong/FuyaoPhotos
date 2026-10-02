package ing.fuyaoskyrocket.photoinfo.features.colors.ui.photo

import android.graphics.Bitmap
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.SampledColor
import ing.fuyaoskyrocket.photoinfo.features.colors.presentation.model.PhotoViewportTransform
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoSpacing

private data class ImageViewRenderState(
    val bitmap: Bitmap,
    val layout: ImageLayout?,
)

/**
 * Displays the bitmap via an [ImageView] in MATRIX mode and overlays a transparent
 * [Canvas] that handles single-finger sampling and two-finger zoom/pan.
 *
 * [viewportTransform] and [onViewportTransformChange] form an atomic pair: zoom and
 * pan are always committed together to avoid mid-frame desynchronisation between
 * the image matrix, sampling coordinates, and the indicator overlay.
 */
@Composable
internal fun SampleImagePanel(
    bitmap: Bitmap,
    sampledColor: SampledColor?,
    viewportTransform: PhotoViewportTransform,
    onViewportTransformChange: (PhotoViewportTransform) -> Unit,
    onSampleAt: (x: Int, y: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.extraLarge
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var loupeVisible by remember(bitmap) { mutableStateOf(false) }
    var loupePoint by remember(bitmap) { mutableStateOf(Offset.Zero) }

    val layout = layoutFor(
        bitmapSize = IntSize(bitmap.width, bitmap.height),
        viewportSize = viewportSize,
        transform = viewportTransform,
    )

    // Re-clamp pan when viewport size changes (rotation, fold unfold).
    LaunchedEffect(viewportSize, bitmap) {
        if (layout != null) {
            val clampedPan = clampPan(viewportTransform.pan, layout)
            if (clampedPan != viewportTransform.pan) {
                onViewportTransformChange(
                    PhotoViewportTransform(viewportTransform.zoom, clampedPan),
                )
            }
        }
    }

    Box(
        modifier = modifier
            .clip(androidx.compose.ui.graphics.RectangleShape)
            .onSizeChanged { viewportSize = it },
    ) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.MATRIX
                    contentDescription = ctx.getString(R.string.cp_image_content_description)
                }
            },
            update = { iv ->
                val previous = iv.tag as? ImageViewRenderState
                if (previous?.bitmap !== bitmap) {
                    iv.setImageBitmap(bitmap)
                }
                if (layout != null && layout != previous?.layout) {
                    iv.imageMatrix = buildImageMatrix(layout)
                }
                iv.tag = ImageViewRenderState(bitmap = bitmap, layout = layout)
            },
            modifier = Modifier.matchParentSize(),
        )

        Canvas(
            modifier = Modifier
                .matchParentSize()
                .photoSamplingGestures(
                    bitmap,
                    viewportSize,
                    viewportTransform,
                    onViewportTransformChange = onViewportTransformChange,
                    onSampleAt = onSampleAt,
                    onLoupeChange = { point ->
                        if (point != null) loupePoint = point
                        loupeVisible = point != null
                    },
                ),
        ) {}

        if (sampledColor != null && layout != null) {
            SamplingIndicator(
                sampledColor = sampledColor,
                layout = layout,
            )
        }
        if (layout != null) ColorLoupe(bitmap, layout, loupePoint, loupeVisible, sampledColor?.sRgb?.hex)
    }
}

/**
 * Placeholder shown when no image is loaded.
 */
@Composable
internal fun EmptyImagePanel(modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(FuyaoSpacing.large),
        ) {
            Icon(
                painter = painterResource(R.drawable.cp_ic_colorize),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(FuyaoSpacing.content))
            Text(
                text = stringResource(R.string.cp_empty_image_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(FuyaoSpacing.small))
            Text(
                text = stringResource(R.string.cp_empty_image_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
