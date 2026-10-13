package ing.fuyaoskyrocket.photoinfo.ui.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R

@Composable
fun PhotoImportCard(title: String, description: String, @DrawableRes icon: Int, busy: Boolean,
                    onGallery: () -> Unit, onFiles: () -> Unit, onCamera: (() -> Unit)? = null) {
    val galleryLabel = stringResource(R.string.from_gallery)
    val importingLabel = stringResource(R.string.importing_photos)
    FuyaoPageIntro(title, description, icon, actionsInSeparateColumn = true) {
        FilledTonalButton(onGallery, Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }, enabled = !busy,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            colors = ButtonDefaults.filledTonalButtonColors(
                disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )) {
            if (busy) CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize).clearAndSetSemantics { },
                color = LocalContentColor.current, strokeWidth = 2.dp)
            else Icon(painterResource(R.drawable.ic_photo_add), null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Box(contentAlignment = Alignment.Center) {
                Text(if (busy) galleryLabel else importingLabel,
                    Modifier.alpha(0f).clearAndSetSemantics { }, textAlign = TextAlign.Center)
                Text(if (busy) importingLabel else galleryLabel, textAlign = TextAlign.Center)
            }
        }
        OutlinedButton(onFiles, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) {
            Text(stringResource(R.string.from_file), textAlign = TextAlign.Center)
        }
        if (onCamera != null) TextButton(onCamera, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) {
            Text(stringResource(R.string.colors_camera), textAlign = TextAlign.Center)
        }
    }
}
