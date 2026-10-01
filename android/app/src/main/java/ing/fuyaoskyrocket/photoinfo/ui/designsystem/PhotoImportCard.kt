package ing.fuyaoskyrocket.photoinfo.ui.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R

@Composable
fun PhotoImportCard(title: String, description: String, @DrawableRes icon: Int, busy: Boolean,
                    onGallery: () -> Unit, onFiles: () -> Unit, onCamera: (() -> Unit)? = null) {
    FuyaoPageIntro(title, description, icon) {
        FilledTonalButton(onGallery, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) {
            Text(stringResource(R.string.from_gallery))
        }
        OutlinedButton(onFiles, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) {
            Text(stringResource(R.string.from_file))
        }
        if (onCamera != null) OutlinedButton(onCamera, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) {
            Text(stringResource(R.string.colors_camera))
        }
        if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.importing_photos), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
