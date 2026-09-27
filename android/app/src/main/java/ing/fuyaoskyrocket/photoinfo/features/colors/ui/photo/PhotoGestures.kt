package ing.fuyaoskyrocket.photoinfo.features.colors.ui.photo

import android.graphics.Bitmap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.features.colors.presentation.model.PhotoViewportTransform
import kotlin.math.roundToInt

/** Single-finger pixel sampling plus two-finger zoom/pan for the shared photo viewport. */
@Composable
internal fun Modifier.photoSamplingGestures(
    bitmap: Bitmap,
    viewportSize: IntSize,
    viewportTransform: PhotoViewportTransform,
    onViewportTransformChange: (PhotoViewportTransform) -> Unit,
    onSampleAt: (x: Int, y: Int) -> Unit,
): Modifier {
    val density = LocalDensity.current
    val samplingHitSlop = with(density) { 72.dp.toPx() }
    val currentTransformState = rememberUpdatedState(viewportTransform)
    val onTransformChangeState = rememberUpdatedState(onViewportTransformChange)
    val onSampleAtState = rememberUpdatedState(onSampleAt)

    return pointerInput(
        bitmap,
        viewportSize,
        samplingHitSlop,
    ) {
        awaitEachGesture {
            val firstDown = awaitFirstDown(requireUnconsumed = false)
            var multiPointerStarted = false
            var lastSample = IntSize(-1, -1)
            var localTransform = currentTransformState.value

            fun sampleAtFinger(
                fingerPosition: Offset,
                gestureLayout: ImageLayout,
            ) {
                val samplePoint = samplingPointAtFinger(
                    finger = fingerPosition,
                    layout = gestureLayout,
                    hitSlop = samplingHitSlop,
                ) ?: return
                val bitmapPoint = viewportToBitmapForSampling(samplePoint, gestureLayout) ?: return
                val pixel = IntSize(
                    width = bitmapPoint.x.roundToInt().coerceIn(0, bitmap.width - 1),
                    height = bitmapPoint.y.roundToInt().coerceIn(0, bitmap.height - 1),
                )
                if (pixel != lastSample) {
                    lastSample = pixel
                    onSampleAtState.value(pixel.width, pixel.height)
                }
            }

            layoutFor(
                bitmapSize = IntSize(bitmap.width, bitmap.height),
                viewportSize = IntSize(size.width, size.height),
                transform = localTransform,
            )?.let { initialLayout ->
                sampleAtFinger(
                    fingerPosition = firstDown.position,
                    gestureLayout = initialLayout,
                )
            }

            do {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val active = event.changes.filter { it.pressed }
                if (size.width == 0 || size.height == 0) {
                    active.forEach { it.consume() }
                    continue
                }
                val gestureLayout = layoutFor(
                    bitmapSize = IntSize(bitmap.width, bitmap.height),
                    viewportSize = IntSize(size.width, size.height),
                    transform = localTransform,
                )
                when {
                    active.size >= 2 -> {
                        if (!multiPointerStarted) {
                            multiPointerStarted = true
                        } else if (gestureLayout != null) {
                            val previousCentroid = event.calculateCentroid(useCurrent = false)
                            val currentCentroid = event.calculateCentroid(useCurrent = true)
                            if (previousCentroid != Offset.Unspecified &&
                                currentCentroid != Offset.Unspecified
                            ) {
                                val newTransform = gestureLayout.transformAfterTwoFingerGesture(
                                    previousCentroid = previousCentroid,
                                    currentCentroid = currentCentroid,
                                    zoomChange = event.calculateZoom(),
                                )
                                localTransform = newTransform
                                onTransformChangeState.value(newTransform)
                            }
                        }
                    }
                    active.size == 1 && !multiPointerStarted && gestureLayout != null -> {
                        sampleAtFinger(
                            fingerPosition = active.first().position,
                            gestureLayout = gestureLayout,
                        )
                    }
                }
                active.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
