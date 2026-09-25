package ing.fuyaoskyrocket.photoinfo.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoScaffold

/**
 * Reserved destination for the PhotoMap module planned in docs/FUYAO_PHOTOS_ROADMAP_ZH.md.
 * The screen stays out of the editor NavHost until the map integration lands, so no
 * navigation entry points here yet.
 */
@Composable
fun PhotoMapScreen(modifier: Modifier = Modifier) {
    FuyaoScaffold(title = stringResource(R.string.photo_map_title), showTopBar = true) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_map),
                    null,
                    Modifier.size(56.dp),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.photo_map_reserved_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.photo_map_reserved_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
