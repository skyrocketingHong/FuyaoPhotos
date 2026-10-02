package ing.fuyaoskyrocket.photoinfo.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.domain.layout.EditorWorkspacePolicy
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoSpacing

/** The ambient photo reaches the system bars; interactive content respects the safe area. */
@Composable
fun PhotoWorkspaceCanvas(bitmap: Bitmap?, padding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        PhotoAmbientBackdrop(bitmap, featherEdges = false, modifier = Modifier.matchParentSize())
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            content()
        }
    }
}

/** All photo workspaces reserve the same viewport and separate action row. */
@Composable
fun PhotoPreviewStage(
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    media: @Composable BoxScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(Modifier
            .then(if (expanded) Modifier else Modifier.widthIn(max = EditorWorkspacePolicy.COMPACT_COLUMN_WIDTH.dp))
            .fillMaxSize().padding(horizontal = FuyaoSpacing.content)) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center, content = media)
            Spacer(Modifier.height(EditorWorkspacePolicy.ACTION_GAP.dp))
            Row(Modifier.fillMaxWidth().height(EditorWorkspacePolicy.ACTION_HEIGHT.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly, content = actions)
        }
    }
}
