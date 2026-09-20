package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.presentation.EditorState
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoIconButton
import kotlinx.coroutines.delay

@Composable
fun EditorPreviewPane(state: EditorState, original: Boolean, onOriginal: () -> Unit,
    onEnlarge: () -> Unit, modifier: Modifier = Modifier, bottomSafe: Boolean = false) {
    var delayedRendering by remember { mutableStateOf(false) }
    LaunchedEffect(state.busy, state.rendering) {
        delayedRendering = false
        if (state.rendering && !state.busy) { delay(200); delayedRendering = true }
    }
    val media = state.mediaMessage?.let { stringResource(it) }
    val details = state.previewError ?: media?.takeIf { state.preservationBlocked }
    var showDetails by remember(details) { mutableStateOf(false) }
    val footerHeight = maxOf(48.dp, with(LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() * 2 } + 8.dp)
    val bottom = if (bottomSafe) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
    BoxWithConstraints(modifier) {
        // Fit the whole 4:3 viewport when the keyboard or landscape window limits height.
        val frameWidth = minOf(maxWidth, (maxHeight - footerHeight - bottom).coerceAtLeast(0.dp) * 4f / 3f)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(frameWidth).aspectRatio(4f / 3f)) {
                PhotoPreview(if (original) state.original else state.preview, Modifier.fillMaxSize())
                if (state.busy || delayedRendering) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(if (bottomSafe) WindowInsets.navigationBars.only(WindowInsetsSides.Bottom) else WindowInsets(0, 0, 0, 0))
                    .heightIn(min = footerHeight).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val status = if (state.exporting) stringResource(R.string.exporting) else details ?: media
                    Text(listOfNotNull("${state.width} × ${state.height}", status).joinToString(" · "),
                        Modifier.weight(1f).then(if (details == null) Modifier else Modifier.clickable { showDetails = true }),
                        style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (details == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    FilterChip(original, onClick = onOriginal, label = { Text(stringResource(R.string.original)) })
                    FuyaoIconButton(R.drawable.ic_expand, stringResource(R.string.enlarge), onEnlarge, enabled = state.preview != null)
                }
            }
        }
    }
    if (showDetails && details != null) AlertDialog(onDismissRequest = { showDetails = false },
        title = { Text(stringResource(R.string.error_title)) }, text = { Text(details) },
        confirmButton = { TextButton(onClick = { showDetails = false }) { Text(stringResource(R.string.close)) } })
}
