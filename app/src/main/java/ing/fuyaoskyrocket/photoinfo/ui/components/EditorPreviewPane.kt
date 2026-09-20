package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.collect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.presentation.EditorState
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoIconButton
import kotlinx.coroutines.delay

@Composable
fun EditorPreviewPane(state: EditorState, onSelectPhoto: (Int) -> Unit, original: Boolean, onOriginal: () -> Unit,
    onEnlarge: () -> Unit, modifier: Modifier = Modifier, bottomSafe: Boolean = false) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val selectedIndex by rememberUpdatedState(state.photoIndex)
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
                key(state.sessionId) {
                    val pager = rememberPagerState(initialPage = state.photoIndex) { state.photos.size }
                    LaunchedEffect(pager) {
                        snapshotFlow { pager.settledPage }.collect { page ->
                            if (page != selectedIndex) {
                                focus.clearFocus(force = true)
                                keyboard?.hide()
                                onSelectPhoto(page)
                            }
                        }
                    }
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { state.photos[it].id },
                        userScrollEnabled = (!state.busy || state.loadingPhoto) && !state.closing) { page ->
                        if (page == state.photoIndex) {
                            PhotoPreview(if (original) state.original else state.preview, Modifier.fillMaxSize())
                        } else Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
                if (state.busy || delayedRendering) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(if (bottomSafe) WindowInsets.navigationBars.only(WindowInsetsSides.Bottom) else WindowInsets(0, 0, 0, 0))
                    .heightIn(min = footerHeight).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val status = when {
                        state.exporting -> stringResource(R.string.batch_progress, state.exportCompleted, state.exportTotal)
                        state.importing -> stringResource(R.string.importing_photos)
                        state.loadingPhoto -> stringResource(R.string.loading_photo)
                        else -> details ?: media ?: if(state.photos.size > 1) stringResource(R.string.swipe_photos) else null
                    }
                    val position = if (state.photos.size > 1) "${state.photoIndex + 1}/${state.photos.size} · " else ""
                    Text(listOfNotNull("$position${state.width} × ${state.height}", status).joinToString(" · "),
                        Modifier.weight(1f).heightIn(min = footerHeight).wrapContentHeight().then(if (details == null) Modifier else Modifier.clickable { showDetails = true }),
                        style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (details == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    FilterChip(original, onClick = onOriginal, enabled = state.original != null, label = { Text(stringResource(R.string.original)) })
                    FuyaoIconButton(R.drawable.ic_expand, stringResource(R.string.enlarge), onEnlarge, enabled = state.preview != null)
                }
            }
        }
    }
    if (showDetails && details != null) AlertDialog(onDismissRequest = { showDetails = false },
        title = { Text(stringResource(R.string.error_title)) }, text = { Text(details) },
        confirmButton = { TextButton(onClick = { showDetails = false }) { Text(stringResource(R.string.close)) } })
}
