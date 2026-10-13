package ing.fuyaoskyrocket.photoinfo.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as BitmapCanvas
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.Rect as BitmapRect
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import ing.fuyaoskyrocket.photoinfo.platform.graphics.TelegramDustView
import ing.fuyaoskyrocket.photoinfo.platform.graphics.PhotoReassemblyView
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoReassemblyLifecycle
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoMotionTokens
import ing.fuyaoskyrocket.photoinfo.ui.theme.LocalPhotoMotionEnabled
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class DustSource(val owner: Any, val bitmap: Bitmap, val bounds: Rect, val imageMatrix: Matrix?)
private data class DustPresentation(val id: Long, val bitmap: Bitmap, val bounds: Rect, val photo: Boolean, val deadline: Long)
private data class ReassemblyDestination(val owner: Any, val ready: Boolean, val bounds: Rect, val appearance: Any)
private data class ReassemblySnapshot(val bitmap: Bitmap, val bounds: Rect)
private data class ReassemblyPresentation(
    val request: PhotoReassemblyLifecycle.Request,
    val source: ReassemblySnapshot,
    val target: ReassemblySnapshot? = null,
    val targetOwner: Any? = null,
    val appearance: Any? = null,
)

@Stable
class PhotoDissolveController internal constructor() {
    private val sources = mutableMapOf<String, DustSource>()
    internal var origin = androidx.compose.ui.geometry.Offset.Zero
    internal var enabled = true
    private var sequence = 0L
    private var items by mutableStateOf(emptyList<DustPresentation>())
    private val destinations = mutableMapOf<String, ReassemblyDestination>()
    private val reassemblyLifecycle = PhotoReassemblyLifecycle()
    private var reassembly by mutableStateOf<ReassemblyPresentation?>(null)

    internal fun register(id: String, owner: Any, bitmap: Bitmap?, bounds: Rect, imageMatrix: Matrix?) {
        if (bitmap != null && !bitmap.isRecycled && bounds.width > 0 && bounds.height > 0)
            sources[id] = DustSource(owner, bitmap, bounds, imageMatrix?.let(::Matrix))
        else if (sources[id]?.owner === owner) sources.remove(id)
    }

    internal fun unregister(id: String, owner: Any) { if (sources[id]?.owner === owner) sources.remove(id) }

    fun dissolve(id: String) {
        if (reassembly?.request?.feature == id) cancelReassembly("replacement")
        if (!enabled) return
        val source = sources[id] ?: return
        val frame = if (source.imageMatrix == null) fitted(source.bounds, source.bitmap.width, source.bitmap.height) else source.bounds
        val snapshot = try { sdrCopy(source) }
        catch (failure: RuntimeException) { logReassembly("dust_snapshot_${failure.javaClass.simpleName}", id); null }
        catch (failure: OutOfMemoryError) { logReassembly("dust_snapshot_${failure.javaClass.simpleName}", id); null }
        if (snapshot != null) present(snapshot, frame, photo = true)
    }

    fun reassemble(id: String) {
        cancelReassembly("superseded")
        items = emptyList()
        if (!enabled) return
        val source = sources[id]
        if (source == null) { logReassembly("source_unavailable", id); return }
        val request = reassemblyLifecycle.begin(id, SystemClock.uptimeMillis())
        val snapshot = try { sdrCopy(source) }
        catch (failure: RuntimeException) { logReassembly("source_snapshot_${failure.javaClass.simpleName}", id); null }
        catch (failure: OutOfMemoryError) { logReassembly("source_snapshot_${failure.javaClass.simpleName}", id); null }
        if (snapshot == null) { reassemblyLifecycle.cancel(); return }
        val frame = if (source.imageMatrix == null) fitted(source.bounds, source.bitmap.width, source.bitmap.height) else source.bounds
        reassembly = ReassemblyPresentation(request, ReassemblySnapshot(snapshot, frame.translate(-origin)))
    }

    internal fun reassemblyRequest(id: String): Long? = reassembly?.takeIf { it.request.feature == id }?.request?.generation

    internal fun waitingForReassemblyTarget(id: String): Long? = reassembly?.takeIf {
        it.request.feature == id && it.target == null
    }?.request?.generation

    internal fun registerDestination(id: String, owner: Any, ready: Boolean, bounds: Rect, appearance: Any) {
        destinations[id] = ReassemblyDestination(owner, ready, bounds, appearance)
        val active = reassembly?.takeIf { it.request.feature == id && it.target != null } ?: return
        if (!ready || active.targetOwner !== owner || active.target?.bounds != bounds.translate(-origin) || active.appearance != appearance)
            cancelReassembly("target_changed")
    }

    internal fun unregisterDestination(id: String, owner: Any) {
        if (destinations[id]?.owner !== owner) return
        destinations.remove(id)
        if (reassembly?.request?.feature == id) cancelReassembly("target_removed")
    }

    internal fun provideReassemblyTarget(generation: Long, id: String, owner: Any, bitmap: Bitmap, bounds: Rect, appearance: Any) {
        val active = reassembly?.takeIf { it.request.generation == generation && it.request.feature == id && it.target == null } ?: return
        val destination = destinations[id] ?: return
        if (destination.owner !== owner || !destination.ready || destination.bounds != bounds || destination.appearance != appearance) return
        if (bitmap.width.toLong() * bitmap.height > PhotoMotionTokens.snapshotPixelLimit) {
            failReassembly(generation, "target_pixel_limit")
            return
        }
        val snapshot = try { sdrCopy(DustSource(owner, bitmap, bounds, null), maximumDimension = Float.POSITIVE_INFINITY) }
        catch (failure: RuntimeException) { failReassembly(generation, "target_snapshot_${failure.javaClass.simpleName}"); null }
        catch (failure: OutOfMemoryError) { failReassembly(generation, "target_snapshot_${failure.javaClass.simpleName}"); null }
        if (snapshot == null) return
        if (!enabled || !reassemblyLifecycle.acceptsTarget(generation, id, SystemClock.uptimeMillis())) {
            snapshot.recycle()
            failReassembly(generation, "target_expired")
            return
        }
        reassembly = active.copy(target = ReassemblySnapshot(snapshot, bounds.translate(-origin)), targetOwner = owner, appearance = appearance)
    }

    internal fun failReassembly(generation: Long, stage: String) {
        if (reassembly?.request?.generation == generation) cancelReassembly(stage)
    }

    internal fun cancelReassembly(reason: String = "cancelled") {
        reassembly?.let { logReassembly(reason, it.request.feature) }
        reassemblyLifecycle.cancel()
        reassembly = null
    }

    private fun logReassembly(stage: String, feature: String) {
        val safeFeature = when (feature) { "cards-photo", "metadata-photo", "colors-photo" -> feature; else -> "other" }
        Log.d("PhotoReassembly", "stage=$stage feature=$safeFeature")
    }

    internal fun present(bitmap: Bitmap, bounds: Rect, photo: Boolean) {
        if (!enabled) return
        items = (items + DustPresentation(++sequence, bitmap, bounds.translate(-origin), photo,
            SystemClock.uptimeMillis() + if (photo) 6_000 else 3_000)).takeLast(3)
    }

    internal fun clear() { items = emptyList(); cancelReassembly("cleared") }

    @Composable
    internal fun Overlay() {
        val context = LocalContext.current
        val density = LocalDensity.current.density
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
        val assembly = reassembly
        LaunchedEffect(assembly?.request?.generation) {
            val request = assembly?.request ?: return@LaunchedEffect
            delay((request.targetDeadline - SystemClock.uptimeMillis()).coerceAtLeast(0))
            if (reassembly?.request?.generation == request.generation && reassemblyLifecycle.expiry(SystemClock.uptimeMillis()) != null)
                failReassembly(request.generation, "target_timeout")
            delay((request.deadline - SystemClock.uptimeMillis()).coerceAtLeast(0))
            failReassembly(request.generation, "lifetime_timeout")
        }
        if (assembly != null) key(assembly.request.generation) {
            var gpuReady by remember { mutableStateOf(false) }
            if (!gpuReady) Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                drawSnapshot(assembly.source.bitmap, assembly.source.bounds)
            }
            assembly.target?.let { target ->
                AndroidView(factory = {
                    PhotoReassemblyView(context, assembly.source.bitmap, assembly.source.bounds.toAndroidRect(),
                        target.bitmap, target.bounds.toAndroidRect(), density,
                        onReady = { gpuReady = true },
                        onComplete = {
                            if (reassemblyLifecycle.finish(assembly.request.generation)) reassembly = null
                        },
                        onFailure = { failReassembly(assembly.request.generation, "gpu_$it") })
                }, modifier = Modifier.fillMaxSize().clearAndSetSemantics { }, onRelease = { it.release() })
            }
        }
    }

    private fun DrawScope.drawSnapshot(item: DustPresentation) {
        drawSnapshot(item.bitmap, item.bounds)
    }

    private fun DrawScope.drawSnapshot(bitmap: Bitmap, bounds: Rect) {
        withTransform({
            translate(bounds.left, bounds.top)
            scale(bounds.width / bitmap.width, bounds.height / bitmap.height, pivot = androidx.compose.ui.geometry.Offset.Zero)
        }) { drawImage(bitmap.asImageBitmap()) }
    }

    private fun fitted(bounds: Rect, width: Int, height: Int): Rect {
        val scale = minOf(bounds.width / width, bounds.height / height)
        val w = width * scale
        val h = height * scale
        val left = bounds.left + ((bounds.width.roundToInt() - w.roundToInt()) / 2f).roundToInt()
        val top = bounds.top + ((bounds.height.roundToInt() - h.roundToInt()) / 2f).roundToInt()
        return Rect(left, top, left + w, top + h)
    }

    private fun sdrCopy(source: DustSource, maximumDimension: Float = 1600f): Bitmap {
        val fullWidth = if (source.imageMatrix == null) source.bitmap.width.toFloat() else source.bounds.width
        val fullHeight = if (source.imageMatrix == null) source.bitmap.height.toFloat() else source.bounds.height
        val scale = minOf(1f, maximumDimension / maxOf(fullWidth, fullHeight))
        val width = (fullWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (fullHeight * scale).roundToInt().coerceAtLeast(1)
        val software = if (source.bitmap.config == Bitmap.Config.HARDWARE)
            requireNotNull(source.bitmap.copy(Bitmap.Config.ARGB_8888, false)) else source.bitmap
        try {
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = BitmapCanvas(result)
                if (source.imageMatrix == null) canvas.drawBitmap(software, null, BitmapRect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
                else {
                    canvas.scale(width / fullWidth, height / fullHeight)
                    canvas.concat(source.imageMatrix)
                    canvas.drawBitmap(software, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                }
                return result
            } catch (failure: Throwable) {
                result.recycle()
                throw failure
            }
        } finally {
            if (software !== source.bitmap) software.recycle()
        }
    }
}

private fun Rect.toAndroidRect() = RectF(left, top, right, bottom)

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
    val currentBitmap by rememberUpdatedState(bitmap)
    val currentMatrix by rememberUpdatedState(imageMatrix)
    SideEffect { controller.register(id, owner, bitmap, bounds, imageMatrix) }
    DisposableEffect(controller, id, owner) { onDispose { controller.unregister(id, owner) } }
    return onGloballyPositioned {
        bounds = it.boundsInRoot()
        controller.register(id, owner, currentBitmap, bounds, currentMatrix)
    }
}
