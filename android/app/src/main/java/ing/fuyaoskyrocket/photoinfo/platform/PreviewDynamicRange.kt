package ing.fuyaoskyrocket.photoinfo.platform

import android.content.pm.ActivityInfo
import android.os.Build
import android.graphics.Bitmap
import android.graphics.ColorSpace
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

fun Bitmap?.hasHdrPreviewContent(): Boolean {
    val bitmap = this ?: return false
    if (Build.VERSION.SDK_INT < 34) return false
    return bitmap.hasGainmap() || bitmap.colorSpace == ColorSpace.get(ColorSpace.Named.BT2020_HLG) ||
        bitmap.colorSpace == ColorSpace.get(ColorSpace.Named.BT2020_PQ)
}

/** Preview-only switch: never detach or edit the source bitmap's gainmap. */
@Composable
fun PreviewDynamicRange(hasHdrContent: Boolean, enabled: Boolean) {
    val window=LocalActivity.current?.window
    DisposableEffect(window,hasHdrContent,enabled) {
        val previous=window?.colorMode
        window?.colorMode=if(Build.VERSION.SDK_INT>=34 && hasHdrContent && enabled) ActivityInfo.COLOR_MODE_HDR
            else ActivityInfo.COLOR_MODE_DEFAULT
        onDispose { if(previous!=null)window?.colorMode=previous }
    }
}
