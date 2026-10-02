package ing.fuyaoskyrocket.photoinfo.ui.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.ui.theme.LocalPhotoMotionEnabled

@Composable
fun PhotoImportCard(title: String, description: String, @DrawableRes icon: Int, busy: Boolean,
                    onGallery: () -> Unit, onFiles: () -> Unit, onCamera: (() -> Unit)? = null) {
    val motionEnabled = LocalPhotoMotionEnabled.current
    FuyaoPageIntro(title, description, icon, actionsInSeparateColumn = true) {
        FilledTonalButton(onGallery, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) {
            Text(stringResource(R.string.from_gallery))
        }
        OutlinedButton(onFiles, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) {
            Text(stringResource(R.string.from_file))
        }
        if (onCamera != null) TextButton(onCamera, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) {
            Text(stringResource(R.string.colors_camera))
        }
        AnimatedVisibility(busy,
            enter = if (motionEnabled) fadeIn(tween(160)) + expandVertically(tween(200)) else EnterTransition.None,
            exit = if (motionEnabled) fadeOut(tween(120)) + shrinkVertically(tween(180)) else ExitTransition.None) {
            Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.importing_photos), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
