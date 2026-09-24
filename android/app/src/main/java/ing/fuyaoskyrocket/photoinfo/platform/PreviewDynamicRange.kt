package ing.fuyaoskyrocket.photoinfo.platform

import android.content.pm.ActivityInfo
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/** Preview-only switch: never detach or edit the source bitmap's gainmap. */
@Composable
fun PreviewDynamicRange(hasGainmap: Boolean, enabled: Boolean) {
    val window=LocalActivity.current?.window
    DisposableEffect(window,hasGainmap,enabled) {
        val previous=window?.colorMode
        window?.colorMode=if(Build.VERSION.SDK_INT>=34 && hasGainmap && enabled) ActivityInfo.COLOR_MODE_HDR
            else ActivityInfo.COLOR_MODE_DEFAULT
        onDispose { if(previous!=null)window?.colorMode=previous }
    }
}
