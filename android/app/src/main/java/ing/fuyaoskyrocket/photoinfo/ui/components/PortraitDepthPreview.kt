package ing.fuyaoskyrocket.photoinfo.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ing.fuyaoskyrocket.photoinfo.domain.media.XiaomiPortraitDepth
import ing.fuyaoskyrocket.photoinfo.domain.media.XiaomiPortraitTail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.io.File

@Composable
internal fun rememberPortraitDepthLayer(file: File?): Bitmap? {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file) {
        if (file == null) return@LaunchedEffect
        var pending: Bitmap? = null
        try {
            val result = withContext(Dispatchers.Default) { renderDepth(file).also { pending = it } }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            bitmap = result
            pending = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { bitmap = null }
        catch (_: OutOfMemoryError) { bitmap = null }
        finally { pending?.recycle() }
    }
    return bitmap
}

private fun renderDepth(file: File): Bitmap? {
    val envelope = ing.fuyaoskyrocket.photoinfo.domain.media.MotionPhoto.inspect(file, "image/jpeg")
    val part = envelope.portraitTail ?: return null
    val tail = XiaomiPortraitTail.read(file, part)
    val decoded = XiaomiPortraitDepth.decode(tail, envelope.portraitDepthDegrees)
    val plane = decoded.disparity
    val upright = Bitmap.createBitmap(plane.width, plane.height, Bitmap.Config.ARGB_8888)
    val row = IntArray(plane.width)
    for (y in 0 until plane.height) {
        for (x in 0 until plane.width) {
            // Disparity already increases toward the camera; near subjects render lighter.
            val value = plane.pixels[y * plane.width + x].toInt() and 255
            row[x] = Color.rgb(value, value, value)
        }
        upright.setPixels(row, 0, plane.width, 0, y, plane.width, 1)
    }
    if (decoded.orientation !in 2..8) return upright
    // The plane lives in sensor orientation; the same EXIF turn the exporter applies
    // brings it upright, otherwise portrait shots show cropped and mirrored.
    val matrix = android.graphics.Matrix().apply {
        when (decoded.orientation) {
            2 -> setScale(-1f, 1f)
            3 -> setRotate(180f)
            4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }
            6 -> setRotate(90f)
            7 -> { setRotate(90f); postScale(1f, -1f) }
            else -> setRotate(270f)
        }
    }
    val rotated = Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, matrix, true)
    if (rotated !== upright) upright.recycle()
    return rotated
}
