package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoMotionTokens
import ing.fuyaoskyrocket.photoinfo.ui.theme.LocalPhotoMotionEnabled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.hypot

/** Settings-only snapshot: keep the new live tree and its draft while revealing its theme. */
@Composable
internal fun ThemeReveal(content: @Composable (change: (() -> Unit) -> Unit) -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val motion = LocalPhotoMotionEnabled.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    var size by remember { mutableStateOf(IntSize.Zero) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var revealJob by remember { mutableStateOf<Job?>(null) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(motion, size, visible) {
        if (!motion || !visible || snapshot?.let { it.width != size.width || it.height != size.height } == true) {
            revealJob?.cancel()
            snapshot = null
            capturing = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().onSizeChanged { size = it }.drawWithContent {
            when {
                capturing -> drawLayer(layer)
                snapshot != null -> drawContent()
                else -> {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
            }
        }) {
            content { apply ->
                if (!capturing && snapshot == null) {
                    if (!motion || !visible || size.width == 0 || size.height == 0 ||
                        size.width.toLong() * size.height > PhotoMotionTokens.snapshotPixelLimit) apply()
                    else {
                        capturing = true
                        revealJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                // Saving is synchronous; cancellation only drops the old visual layer.
                                apply()
                                val old = try { layer.toImageBitmap() } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: RuntimeException) { null }
                                progress.snapTo(0f)
                                snapshot = old
                                capturing = false
                                if (old != null) {
                                    withFrameNanos { }
                                    progress.animateTo(1f, tween(PhotoMotionTokens.themeMillis, easing = FastOutSlowInEasing))
                                }
                            } finally {
                                capturing = false
                                snapshot = null
                            }
                        }
                    }
                }
            }
        }
        if (capturing || snapshot != null) Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }) {
            snapshot?.let { image ->
                val origin = Offset(size.width * .85f, size.height.toFloat())
                val radius = hypot(size.width.toFloat(), size.height.toFloat()) * progress.value
                val hole = Path().apply { addOval(Rect(origin.x - radius, origin.y - radius, origin.x + radius, origin.y + radius)) }
                clipPath(hole, ClipOp.Difference) { drawImage(image) }
            }
        }
    }
}
