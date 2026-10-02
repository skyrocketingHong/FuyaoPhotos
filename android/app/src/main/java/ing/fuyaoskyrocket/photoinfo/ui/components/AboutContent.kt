package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.BuildConfig
import ing.fuyaoskyrocket.photoinfo.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutContent() {
    val links = LocalUriHandler.current
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.about_version,BuildConfig.MARKETING_VERSION,BuildConfig.BUILD_NUMBER,BuildConfig.BUILD_TYPE),style=MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.about_features),style=MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.about_credit) + "\n" + stringResource(R.string.about_colors_credit),
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            TextButton({ links.openUri("https://github.com/skyrocketingHong/FuyaoPhotos") }) { Text("GitHub") }
            TextButton({ links.openUri("https://github.com/skyrocketingHong/FuyaoPhotos/blob/main/LICENSE") }) { Text("AGPL-3.0-only") }
        }
    }
}
