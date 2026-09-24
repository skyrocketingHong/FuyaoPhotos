package ing.fuyaoskyrocket.photoinfo.platform

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/** Same transparent system-bar contract as the other Fuyao Android utilities. */
fun ComponentActivity.enableFuyaoEdgeToEdge() {
    val dark=resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val bars=if(dark)SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT,Color.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle=bars,navigationBarStyle=bars)
    if(Build.VERSION.SDK_INT>=29)window.isNavigationBarContrastEnforced=false
}
