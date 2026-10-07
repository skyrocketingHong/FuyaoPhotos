package ing.fuyaoskyrocket.photoinfo.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoMotionTokens
import ing.fuyaoskyrocket.photoinfo.ui.theme.LocalPhotoMotionEnabled
import kotlinx.coroutines.CancellationException

/** Captures the last ordinary row once, then hands it to the original GPU dust renderer. */
@Composable
internal fun DissolvingRow(removed: Boolean, onFinished: () -> Unit, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val controller = LocalPhotoDissolve.current
    val density = LocalDensity.current
    val motion = LocalPhotoMotionEnabled.current
    val finish by rememberUpdatedState(onFinished)
    var measured by remember { mutableStateOf(IntSize.Zero) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(removed, motion) {
        if (!removed) return@LaunchedEffect
        if (!motion || controller == null || measured.width == 0 || measured.height == 0 ||
            measured.width.toLong() * measured.height > PhotoMotionTokens.snapshotPixelLimit) {
            finish()
            return@LaunchedEffect
        }
        try {
            val image = layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
            if (image != null) controller.present(image, bounds, photo = false)
            finish()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) { finish() }
        catch (_: OutOfMemoryError) { finish() }
    }
    if (!removed) {
        Box(Modifier.onSizeChanged { measured = it }.onGloballyPositioned { bounds = it.boundsInRoot() }.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }) { content() }
    } else {
        Box(Modifier.size(with(density) { measured.width.toDp() }, with(density) { measured.height.toDp() })
            .clearAndSetSemantics { }.drawWithContent { drawLayer(layer) })
    }
}
