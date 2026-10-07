package ing.fuyaoskyrocket.photoinfo.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as BitmapCanvas
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.Rect as BitmapRect
import android.graphics.RectF
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import ing.fuyaoskyrocket.photoinfo.platform.graphics.TelegramDustView
import ing.fuyaoskyrocket.photoinfo.ui.theme.LocalPhotoMotionEnabled
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class DustSource(val owner: Any, val bitmap: Bitmap, val bounds: Rect, val imageMatrix: Matrix?)
private data class DustPresentation(val id: Long, val bitmap: Bitmap, val bounds: Rect, val photo: Boolean, val deadline: Long)

@Stable
class PhotoDissolveController internal constructor() {
    private val sources = mutableMapOf<String, DustSource>()
    internal var origin = androidx.compose.ui.geometry.Offset.Zero
    internal var enabled = true
    private var sequence = 0L
    private var items by mutableStateOf(emptyList<DustPresentation>())

    internal fun register(id: String, owner: Any, bitmap: Bitmap?, bounds: Rect, imageMatrix: Matrix?) {
        if (bitmap != null && !bitmap.isRecycled && bounds.width > 0 && bounds.height > 0)
            sources[id] = DustSource(owner, bitmap, bounds, imageMatrix?.let(::Matrix))
        else if (sources[id]?.owner === owner) sources.remove(id)
    }

    internal fun unregister(id: String, owner: Any) { if (sources[id]?.owner === owner) sources.remove(id) }

    fun dissolve(id: String) {
        if (!enabled) return
        val source = sources[id] ?: return
        val frame = if (source.imageMatrix == null) fitted(source.bounds, source.bitmap.width, source.bitmap.height) else source.bounds
        val snapshot = try { sdrCopy(source) } catch (_: RuntimeException) { null } catch (_: OutOfMemoryError) { null }
        if (snapshot != null) present(snapshot, frame, photo = true)
    }

    internal fun present(bitmap: Bitmap, bounds: Rect, photo: Boolean) {
        if (!enabled) return
        items = (items + DustPresentation(++sequence, bitmap, bounds.translate(-origin), photo,
            SystemClock.uptimeMillis() + if (photo) 6_000 else 3_000)).takeLast(3)
    }

    internal fun clear() { items = emptyList() }

    @Composable
    internal fun Overlay() {
        val context = LocalContext.current
        LaunchedEffect(items.map { it.id }) {
            val next = items.minOfOrNull { it.deadline } ?: return@LaunchedEffect
            delay((next - SystemClock.uptimeMillis()).coerceAtLeast(0))
            items = items.filter { it.deadline > SystemClock.uptimeMillis() }
        }
        for (item in items) key(item.id) {
            var ready by remember { mutableStateOf(false) }
            if (!ready) Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) { drawSnapshot(item) }
            AndroidView(factory = {
                TelegramDustView(context, item.bitmap, RectF(item.bounds.left, item.bounds.top, item.bounds.right, item.bounds.bottom),
                    item.photo, onReady = { ready = true }, onFinished = { items = items.filterNot { it.id == item.id } })
            }, modifier = Modifier.fillMaxSize().clearAndSetSemantics { }, onRelease = { it.release() })
        }
    }

    private fun DrawScope.drawSnapshot(item: DustPresentation) {
        val bounds = item.bounds
        drawImage(item.bitmap.asImageBitmap(), dstOffset = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()),
            dstSize = IntSize(bounds.width.roundToInt().coerceAtLeast(1), bounds.height.roundToInt().coerceAtLeast(1)))
    }

    private fun fitted(bounds: Rect, width: Int, height: Int): Rect {
        val scale = minOf(bounds.width / width, bounds.height / height)
        val w = width * scale
        val h = height * scale
        return Rect(bounds.center.x - w / 2, bounds.center.y - h / 2, bounds.center.x + w / 2, bounds.center.y + h / 2)
    }

    private fun sdrCopy(source: DustSource): Bitmap {
        val fullWidth = if (source.imageMatrix == null) source.bitmap.width.toFloat() else source.bounds.width
        val fullHeight = if (source.imageMatrix == null) source.bitmap.height.toFloat() else source.bounds.height
        val scale = minOf(1f, 1600f / maxOf(fullWidth, fullHeight))
        val width = (fullWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (fullHeight * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            val canvas = BitmapCanvas(it)
            if (source.imageMatrix == null) canvas.drawBitmap(source.bitmap, null, BitmapRect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
            else {
                canvas.scale(width / fullWidth, height / fullHeight)
                canvas.concat(source.imageMatrix)
                canvas.drawBitmap(source.bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
    }
}

internal val LocalPhotoDissolve = staticCompositionLocalOf<PhotoDissolveController?> { null }

@Composable
fun rememberPhotoDissolveController(): PhotoDissolveController = remember { PhotoDissolveController() }

@Composable
fun PhotoDissolveHost(controller: PhotoDissolveController, content: @Composable () -> Unit) {
    val motion = LocalPhotoMotionEnabled.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val enabled = motion && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    SideEffect { controller.enabled = enabled }
    LaunchedEffect(enabled) { if (!enabled) controller.clear() }
    DisposableEffect(controller) { onDispose { controller.enabled = false; controller.clear() } }
    Box(Modifier.fillMaxSize().onGloballyPositioned {
        val position = it.boundsInRoot().topLeft
        if (position != controller.origin) { controller.clear(); controller.origin = position }
    }) {
        CompositionLocalProvider(LocalPhotoDissolve provides controller) { content() }
        controller.Overlay()
    }
}

@Composable
fun Modifier.photoDissolveSource(id: String, bitmap: Bitmap?, imageMatrix: Matrix? = null): Modifier {
    val controller = LocalPhotoDissolve.current ?: return this
    val owner = remember { Any() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    SideEffect { controller.register(id, owner, bitmap, bounds, imageMatrix) }
    DisposableEffect(controller, id, owner) { onDispose { controller.unregister(id, owner) } }
    return onGloballyPositioned { bounds = it.boundsInRoot() }
}
