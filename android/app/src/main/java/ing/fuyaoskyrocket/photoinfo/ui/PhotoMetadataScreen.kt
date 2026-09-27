package ing.fuyaoskyrocket.photoinfo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.presentation.EditorState
import ing.fuyaoskyrocket.photoinfo.ui.components.PhotoInfoContent
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoScaffold
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoLayout

@Composable
fun PhotoMetadataScreen(state: EditorState, onOpenCards: () -> Unit, onSelectPhoto: (Int) -> Unit) {
    FuyaoScaffold(title = stringResource(R.string.photo_metadata_title)) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = FuyaoLayout.readable).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_info), null, Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.photo_metadata_title),
                        Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.photo_metadata_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.photos.size > 1) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSelectPhoto(state.photoIndex - 1) },
                        enabled = !state.busy && state.photoIndex > 0) {
                        Icon(painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.previous_photo))
                    }
                    Text(stringResource(R.string.photo_position, state.photoIndex + 1, state.photos.size),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { onSelectPhoto(state.photoIndex + 1) },
                        enabled = !state.busy && state.photoIndex < state.photos.lastIndex) {
                        Icon(painterResource(R.drawable.ic_chevron),
                            contentDescription = stringResource(R.string.next_photo))
                    }
                }
            }
            if (state.photoDetails != null) {
                PhotoInfoContent(state, Modifier.fillMaxWidth().weight(1f), showThumbnail = false)
            } else {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.loadingPhoto || state.importing) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Text(stringResource(R.string.loading_photo), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(stringResource(if (state.photos.isEmpty()) R.string.photo_metadata_empty
                                else R.string.photo_metadata_unavailable),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = onOpenCards) { Text(stringResource(R.string.photo_metadata_open_cards)) }
                        }
                    }
                }
            }
        }
        }
    }
}
