package ing.fuyaoskyrocket.photoinfo.ui.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat

private val Darkroom=darkColorScheme(
    primary=Color(0xFFFFDA45),onPrimary=Color.Black,secondary=Color(0xFFE4D99C),
    secondaryContainer=Color(0xFF302A12),onSecondaryContainer=Color(0xFFFFDA45),
    background=Color.Black,surface=Color.Black,onSurface=Color(0xFFFFDA45),
    onSurfaceVariant=Color(0xFFBDB7A8),surfaceContainer=Color.Black,surfaceContainerLow=Color(0xFF111111),
    surfaceContainerHigh=Color(0xFF202020),outline=Color(0xFF817A5B))

@Composable
fun EditorDarkroomTheme(enabled:Boolean,content:@Composable ()->Unit) {
    val activity=LocalActivity.current
    DisposableEffect(activity,enabled) {
        val window=activity?.window
        val controller=window?.let { WindowCompat.getInsetsController(it,it.decorView) }
        val status=controller?.isAppearanceLightStatusBars
        val navigation=controller?.isAppearanceLightNavigationBars
        if(enabled) { controller?.isAppearanceLightStatusBars=false;controller?.isAppearanceLightNavigationBars=false }
        onDispose {
            if(status!=null)controller?.isAppearanceLightStatusBars=status
            if(navigation!=null)controller?.isAppearanceLightNavigationBars=navigation
        }
    }
    MaterialTheme(colorScheme=if(enabled)Darkroom else MaterialTheme.colorScheme,content=content)
}
