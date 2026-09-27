package ing.fuyaoskyrocket.photoinfo.features.colors.ui.photo

import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import ing.fuyaoskyrocket.photoinfo.features.colors.presentation.model.PhotoViewportTransform
import kotlin.math.abs
import kotlin.math.min

/**
 * Pre-computed layout describing how a bitmap maps into a viewport for a given
 * [PhotoViewportTransform].
 */
internal data class ImageLayout(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val bitmapWidth: Float,
    val bitmapHeight: Float,
    val transform: PhotoViewportTransform,
) {
    val baseScale: Float
        get() = min(viewportWidth / bitmapWidth, viewportHeight / bitmapHeight)
    val renderScale: Float
        get() = baseScale * transform.zoom
    val renderedWidth: Float
        get() = bitmapWidth * renderScale
    val renderedHeight: Float
        get() = bitmapHeight * renderScale
    val centeredImageOffset: Offset
        get() = Offset(
            (viewportWidth - renderedWidth) / 2f,
            (viewportHeight - renderedHeight) / 2f,
        )
    val imageOffset: Offset
        get() = centeredImageOffset + transform.pan
    val imageBounds: Rect
        get() = Rect(
            offset = imageOffset,
            size = androidx.compose.ui.geometry.Size(renderedWidth, renderedHeight),
        )
}

/** Creates an [ImageLayout] for the given sizes and transform, or null if sizes are invalid. */
internal fun layoutFor(
    bitmapSize: IntSize,
    viewportSize: IntSize,
    transform: PhotoViewportTransform,
): ImageLayout? {
    if (viewportSize.width <= 0 || viewportSize.height <= 0 ||
        bitmapSize.width <= 0 || bitmapSize.height <= 0
    ) return null
    return ImageLayout(
        viewportWidth = viewportSize.width.toFloat(),
        viewportHeight = viewportSize.height.toFloat(),
        bitmapWidth = bitmapSize.width.toFloat(),
        bitmapHeight = bitmapSize.height.toFloat(),
        transform = transform,
    )
}

/**
 * Converts a viewport point to bitmap coordinates for sampling.
 * Returns null when the point falls outside the bitmap bounds.
 */
internal fun viewportToBitmapForSampling(point: Offset, layout: ImageLayout): Offset? {
    val bounds = layout.imageBounds
    if (point.x < bounds.left || point.x > bounds.right ||
        point.y < bounds.top || point.y > bounds.bottom
    ) return null

    val bitmapX = (point.x - layout.imageOffset.x) / layout.renderScale
    val bitmapY = (point.y - layout.imageOffset.y) / layout.renderScale
    return Offset(
        bitmapX.coerceIn(0f, layout.bitmapWidth - 1f),
        bitmapY.coerceIn(0f, layout.bitmapHeight - 1f),
    )
}

/**
 * Converts a viewport point to bitmap coordinates without bounds checking.
 * Used for zoom anchors that may fall in the letterbox area.
 */
internal fun viewportToBitmapUnbounded(point: Offset, layout: ImageLayout): Offset {
    return Offset(
        (point.x - layout.imageOffset.x) / layout.renderScale,
        (point.y - layout.imageOffset.y) / layout.renderScale,
    )
}

/** Converts a bitmap point back to viewport coordinates (for indicator drawing). */
internal fun bitmapToViewport(point: Offset, layout: ImageLayout): Offset {
    return Offset(
        layout.imageOffset.x + point.x * layout.renderScale,
        layout.imageOffset.y + point.y * layout.renderScale,
    )
}

/**
 * Keeps the transformed image reachable without treating the letterbox as a
 * forbidden area. A smaller image may move through the available letterbox;
 * a larger image may move until either image edge meets the viewport edge.
 */
internal fun clampPan(pan: Offset, layout: ImageLayout): Offset {
    val horizontalRange = abs(layout.viewportWidth - layout.renderedWidth) / 2f
    val verticalRange = abs(layout.viewportHeight - layout.renderedHeight) / 2f
    return Offset(
        pan.x.coerceIn(-horizontalRange, horizontalRange),
        pan.y.coerceIn(-verticalRange, verticalRange),
    )
}

/**
 * Computes the new transform after a two-finger gesture event.
 *
 * The zoom anchor is the bitmap pixel that was under the *previous* centroid,
 * and it is moved to the *current* centroid. This naturally includes both zoom
 * and translation without needing to separately call [calculatePan].
 *
 * First multi-touch frame should be skipped by the caller to establish the baseline.
 */
internal fun ImageLayout.transformAfterTwoFingerGesture(
    previousCentroid: Offset,
    currentCentroid: Offset,
    zoomChange: Float,
): PhotoViewportTransform {
    val anchor = viewportToBitmapUnbounded(previousCentroid, this)
    val newZoom = (transform.zoom * zoomChange).coerceIn(1f, 8f)
    val newRenderScale = baseScale * newZoom
    val newCenteredOffset = Offset(
        x = (viewportWidth - bitmapWidth * newRenderScale) / 2f,
        y = (viewportHeight - bitmapHeight * newRenderScale) / 2f,
    )
    val newPan = Offset(
        x = currentCentroid.x - newCenteredOffset.x - anchor.x * newRenderScale,
        y = currentCentroid.y - newCenteredOffset.y - anchor.y * newRenderScale,
    )
    val trialLayout = copy(transform = PhotoViewportTransform(zoom = newZoom, pan = newPan))
    return PhotoViewportTransform(zoom = newZoom, pan = clampPan(newPan, trialLayout))
}

/** Builds the [Matrix] that an ImageView should use to render the bitmap for [layout]. */
internal fun buildImageMatrix(layout: ImageLayout): Matrix {
    return Matrix().apply {
        setScale(layout.renderScale, layout.renderScale)
        postTranslate(layout.imageOffset.x, layout.imageOffset.y)
    }
}
