package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.IntSize
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoMotionTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

@Composable
fun PhotoReassemblyTarget(id: String, ready: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val controller = LocalPhotoDissolve.current
    if (controller == null) { Box(modifier) { content() }; return }
    val layer = rememberGraphicsLayer()
    val owner = remember { Any() }
    val density = LocalDensity.current
    val appearance = listOf(density.density, density.fontScale, LocalLayoutDirection.current,
        MaterialTheme.colorScheme, MaterialTheme.typography)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    val currentReady by rememberUpdatedState(ready)
    val currentAppearance by rememberUpdatedState(appearance)
    val generation = controller.reassemblyRequest(id)
    val waiting = controller.waitingForReassemblyTarget(id)
    val hidden = generation != null
    val recorded = remember(waiting, ready, bounds, measured, appearance) { CompletableDeferred<Unit>() }
    SideEffect { controller.registerDestination(id, owner, ready, bounds, appearance) }
    DisposableEffect(controller, id, owner) { onDispose { controller.unregisterDestination(id, owner) } }
    LaunchedEffect(waiting, ready, bounds, measured, appearance) {
        val request = waiting ?: return@LaunchedEffect
        if (!ready || bounds.width <= 0 || bounds.height <= 0 || measured.width <= 0 || measured.height <= 0) return@LaunchedEffect
        if (measured.width.toLong() * measured.height > PhotoMotionTokens.snapshotPixelLimit) {
            controller.failReassembly(request, "target_pixel_limit")
            return@LaunchedEffect
        }
        try {
            recorded.await()
            withFrameNanos { }
            val bitmap = layer.toImageBitmap().asAndroidBitmap()
            try { controller.provideReassemblyTarget(request, id, owner, bitmap, bounds, appearance) }
            finally { bitmap.recycle() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RuntimeException) {
            controller.failReassembly(request, "target_record_${failure.javaClass.simpleName}")
        } catch (failure: OutOfMemoryError) {
            controller.failReassembly(request, "target_record_${failure.javaClass.simpleName}")
        }
    }
    val inputGate = if (!hidden) Modifier else Modifier
        .focusProperties { onEnter = { cancelFocusChange() } }.focusGroup()
        .onPreviewKeyEvent { true }
        .pointerInput(generation) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
        .clearAndSetSemantics { hideFromAccessibility() }
    Box(modifier.then(inputGate)
        .onSizeChanged { measured = it }
        .onGloballyPositioned {
            bounds = it.boundsInRoot()
            controller.registerDestination(id, owner, currentReady, bounds, currentAppearance)
        }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            recorded.complete(Unit)
            if (!hidden) drawLayer(layer)
        }) {
        content()
    }
}
