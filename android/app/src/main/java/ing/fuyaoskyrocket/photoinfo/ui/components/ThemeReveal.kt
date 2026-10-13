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
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInputModeManager
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
import kotlin.math.max

internal val LocalThemeRevealKeyboardCancel = staticCompositionLocalOf<() -> Unit> { {} }

/** Keep the live settings tree while revealing its new theme over the previous appearance. */
@Composable
internal fun ThemeReveal(content: @Composable (change: (() -> Unit) -> Unit) -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val motion = LocalPhotoMotionEnabled.current
    val inputModeManager = LocalInputModeManager.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    var size by remember { mutableStateOf(IntSize.Zero) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var revealJob by remember { mutableStateOf<Job?>(null) }
    var revealGeneration by remember { mutableIntStateOf(0) }
    var touchOrigin by remember { mutableStateOf<Offset?>(null) }
    var revealOrigin by remember { mutableStateOf(Offset.Zero) }
    val progress = remember { Animatable(1f) }
    fun stopReveal() {
        revealGeneration++
        revealJob?.cancel()
        revealJob = null
        snapshot = null
        capturing = false
    }
    val cancelForKeyboard = remember { {
        touchOrigin = null
        stopReveal()
    } }
    LaunchedEffect(motion, size, visible) {
        stopReveal()
        touchOrigin = null
    }
    Box(Modifier.fillMaxSize()
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                cancelForKeyboard()
            }
            false
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) {
                        val interrupted = capturing || snapshot != null
                        stopReveal()
                        touchOrigin = if (interrupted) null else event.changes.firstOrNull { it.pressed }?.position
                    }
                }
            }
        }) {
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
            CompositionLocalProvider(LocalThemeRevealKeyboardCancel provides cancelForKeyboard) {
            content { apply ->
                val origin = touchOrigin
                touchOrigin = null
                val canAnimate = motion && visible && inputModeManager.inputMode == InputMode.Touch &&
                    origin != null && !capturing && snapshot == null && size.width > 0 && size.height > 0 &&
                    size.width.toLong() * size.height <= PhotoMotionTokens.snapshotPixelLimit
                stopReveal()
                if (!canAnimate || origin == null) apply()
                else {
                    revealOrigin = Offset(origin.x.coerceIn(0f, size.width.toFloat()),
                        origin.y.coerceIn(0f, size.height.toFloat()))
                    val generation = revealGeneration
                    capturing = true
                    revealJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            apply()
                            val old = try { layer.toImageBitmap() } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: RuntimeException) { null }
                            if (generation == revealGeneration) {
                                progress.snapTo(0f)
                                snapshot = old
                                capturing = false
                                if (old != null) {
                                    withFrameNanos { }
                                    progress.animateTo(1f, tween(PhotoMotionTokens.themeMillis, easing = FastOutSlowInEasing))
                                }
                            }
                        } finally {
                            if (generation == revealGeneration) {
                                capturing = false
                                snapshot = null
                            }
                        }
                    }
                }
            }
            }
        }
        if (capturing || snapshot != null) Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            snapshot?.let { image ->
                val origin = revealOrigin
                val radius = hypot(max(origin.x, size.width - origin.x), max(origin.y, size.height - origin.y)) * progress.value
                val hole = Path().apply { addOval(Rect(origin.x - radius, origin.y - radius, origin.x + radius, origin.y + radius)) }
                clipPath(hole, ClipOp.Difference) { drawImage(image) }
            }
        }
    }
}
