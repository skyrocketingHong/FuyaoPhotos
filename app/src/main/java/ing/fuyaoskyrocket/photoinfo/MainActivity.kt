package ing.fuyaoskyrocket.photoinfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ing.fuyaoskyrocket.photoinfo.platform.enableFuyaoEdgeToEdge
import ing.fuyaoskyrocket.photoinfo.ui.EditorScreen
import ing.fuyaoskyrocket.photoinfo.ui.theme.PhotoInfoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_HDR
        enableFuyaoEdgeToEdge()
        setContent { PhotoInfoTheme { EditorScreen(onExit = ::finishAndRemoveTask) } }
    }
}
