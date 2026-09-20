package ing.fuyaoskyrocket.photoinfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ing.fuyaoskyrocket.photoinfo.ui.EditorScreen
import ing.fuyaoskyrocket.photoinfo.ui.theme.PhotoInfoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PhotoInfoTheme { EditorScreen() } }
    }
}
