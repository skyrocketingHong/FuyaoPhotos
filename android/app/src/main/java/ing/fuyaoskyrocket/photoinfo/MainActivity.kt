package ing.fuyaoskyrocket.photoinfo

import android.os.Bundle
import android.content.Intent
import androidx.activity.viewModels
import ing.fuyaoskyrocket.photoinfo.presentation.EditorViewModel
import ing.fuyaoskyrocket.photoinfo.platform.PhotoIntents
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ing.fuyaoskyrocket.photoinfo.platform.enableFuyaoEdgeToEdge
import ing.fuyaoskyrocket.photoinfo.ui.EditorScreen
import ing.fuyaoskyrocket.photoinfo.ui.theme.PhotoInfoTheme

class MainActivity : ComponentActivity() {
    private val editor: EditorViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableFuyaoEdgeToEdge()
        if (savedInstanceState == null) receivePhotos(intent)
        setContent { PhotoInfoTheme { EditorScreen(vm = editor, onExit = ::finishAndRemoveTask) } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receivePhotos(intent)
    }
    private fun receivePhotos(intent: Intent) {
        try { PhotoIntents.sharedImages(intent)?.let(editor::receiveSharedPhotos) }
        catch (_: RuntimeException) { editor.reportExternalError(R.string.shared_photo_invalid) }
    }
}
