package ing.fuyaoskyrocket.photoinfo.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.data.photo.PhotoRepository
import ing.fuyaoskyrocket.photoinfo.domain.media.XiaomiPortraitDepth
import ing.fuyaoskyrocket.photoinfo.domain.media.XiaomiPortraitTail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shows the vendor depth plane the importer recognized, near-to-light far-to-dark, so the
 * conversion input is visible before any export happens.
 */
@Composable
internal fun PortraitDepthPreview(file: File, modifier: Modifier = Modifier) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        try {
            bitmap = withContext(Dispatchers.Default) { renderDepth(file) }
        } catch (_: Exception) {
            failed = true
        }
    }
    Box(modifier.fillMaxWidth().heightIn(max = 220.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        val current = bitmap
        when {
            current != null -> Image(
                current.asImageBitmap(),
                contentDescription = stringResource(R.string.portrait_depth_preview),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                    .clip(MaterialTheme.shapes.medium))
            failed -> Text(stringResource(R.string.portrait_depth_unavailable),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
