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

@Composable
internal fun Modifier.photoSamplingGestures(
    bitmap: Bitmap,
    viewportSize: IntSize,
    viewportTransform: PhotoViewportTransform,
    onViewportTransformChange: (PhotoViewportTransform) -> Unit,
    onSampleAt: (x: Int, y: Int) -> Unit,
    onLoupeChange: (Offset?) -> Unit,
): Modifier {
    val hitSlop = with(LocalDensity.current) { 72.dp.toPx() }
    val transformState = rememberUpdatedState(viewportTransform)
    val transformChanged = rememberUpdatedState(onViewportTransformChange)
    val sampleChanged = rememberUpdatedState(onSampleAt)
    val loupeChanged = rememberUpdatedState(onLoupeChange)
    return pointerInput(bitmap, viewportSize, hitSlop) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var pointer = down.position
            var timestamp = down.uptimeMillis
            var picking = false
            var cancelled = down.isConsumed
            var multiPointerStarted = false
            var localTransform = transformState.value
            var lastSample = IntSize(-1, -1)
            fun layout() = layoutFor(IntSize(bitmap.width, bitmap.height), IntSize(size.width, size.height), localTransform)
            fun sample(showLoupe: Boolean) {
                val current = layout() ?: return
                val samplePoint = samplingPointAtFinger(pointer, current, hitSlop) ?: return
                val pixel = viewportToBitmapForSampling(samplePoint, current) ?: return
                val target = IntSize(pixel.x.roundToInt().coerceIn(0, bitmap.width - 1),
                    pixel.y.roundToInt().coerceIn(0, bitmap.height - 1))
                if (target != lastSample) {
                    lastSample = target
                    sampleChanged.value(target.width, target.height)
                }
                if (showLoupe) loupeChanged.value(pointer)
            }
            try {
                while (true) {
                    val event = if (!picking && !cancelled && !multiPointerStarted) {
                        withTimeoutOrNull((200L - (timestamp - down.uptimeMillis)).coerceAtLeast(1L)) {
                            awaitPointerEvent(PointerEventPass.Main)
                        }
                    } else awaitPointerEvent(PointerEventPass.Main)
                    if (event == null) {
                        picking = true
                        sample(showLoupe = true)
                        continue
                    }
                    timestamp = event.changes.maxOf { it.uptimeMillis }
                    val active = event.changes.filter { it.pressed }
                    if (active.isEmpty()) {
                        if (!cancelled && !multiPointerStarted) sample(showLoupe = false)
                        break
                    }
                    if (active.size >= 2) {
                        loupeChanged.value(null)
                        picking = false
                        val current = layout()
                        if (multiPointerStarted && current != null) {
                            val before = event.calculateCentroid(useCurrent = false)
                            val after = event.calculateCentroid(useCurrent = true)
                            if (before != Offset.Unspecified && after != Offset.Unspecified) {
                                localTransform = current.transformAfterTwoFingerGesture(before, after, event.calculateZoom())
                                transformChanged.value(localTransform)
                            }
                        }
                        multiPointerStarted = true
                        active.forEach { it.consume() }
                    } else if (!multiPointerStarted) {
                        val change = active.first()
                        pointer = change.position
                        if (picking) {
                            sample(showLoupe = true)
                            change.consume()
                        } else if (change.isConsumed || (pointer - down.position).getDistance() > viewConfiguration.touchSlop) {
                            cancelled = true
                        }
                    }
                }
            } finally { loupeChanged.value(null) }
        }
    }
}
