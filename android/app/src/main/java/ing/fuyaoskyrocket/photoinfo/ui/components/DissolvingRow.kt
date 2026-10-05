package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import ing.fuyaoskyrocket.photoinfo.domain.motion.ParticleMotion
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoMotionTokens
import ing.fuyaoskyrocket.photoinfo.ui.theme.LocalPhotoMotionEnabled
import kotlinx.coroutines.CancellationException

/** A retained presentation of an already-removed UI row; never capture a photo viewport. */
@Composable
internal fun DissolvingRow(removed: Boolean, onFinished: () -> Unit, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val reverse = LocalLayoutDirection.current == LayoutDirection.Rtl
    val motion = LocalPhotoMotionEnabled.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val finish by rememberUpdatedState(onFinished)
    var measured by remember { mutableStateOf(IntSize.Zero) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    val progress = remember { Animatable(0f) }
    val grid = remember(measured, density) {
        ParticleMotion.grid(measured.width / density.density, measured.height / density.density)
    }

    LaunchedEffect(removed, motion, visible) {
        if (!removed) {
            snapshot = null
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        if (!motion || !visible || grid.count == 0 ||
            measured.width.toLong() * measured.height > PhotoMotionTokens.snapshotPixelLimit) {
            finish()
            return@LaunchedEffect
        }
        try {
            snapshot = layer.toImageBitmap()
            progress.animateTo(1f, tween(PhotoMotionTokens.dissolveMillis, easing = LinearEasing))
            snapshot = null
            finish()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            snapshot = null
            finish()
        }
    }

    if (!removed) {
        Box(Modifier.onSizeChanged { measured = it }.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }) { content() }
    } else {
        Box(Modifier.size(with(density) { measured.width.toDp() }, with(density) { measured.height.toDp() })
            .clearAndSetSemantics { }) {
            Canvas(Modifier.matchParentSize()) {
                val image = snapshot
                if (image == null) {
                    drawLayer(layer)
                    return@Canvas
                }
                for (row in 0 until grid.rows) for (column in 0 until grid.columns) {
                    val index = row * grid.columns + if (reverse) grid.columns - 1 - column else column
                    val frame = ParticleMotion.frame(index, grid.columns, progress.value, reverse)
                    if (frame.alpha <= 0f) continue
                    val left = column * image.width / grid.columns
                    val top = row * image.height / grid.rows
                    val width = (column + 1) * image.width / grid.columns - left
                    val height = (row + 1) * image.height / grid.rows - top
                    val pivot = Offset(left + width / 2f, top + height / 2f)
                    translate(frame.x * density.density, frame.y * density.density) {
                        scale(frame.scale, frame.scale, pivot) {
                            drawImage(image, IntOffset(left, top), IntSize(width, height),
                                IntOffset(left, top), IntSize(width, height), alpha = frame.alpha)
                        }
                    }
                }
            }
        }
    }
}
