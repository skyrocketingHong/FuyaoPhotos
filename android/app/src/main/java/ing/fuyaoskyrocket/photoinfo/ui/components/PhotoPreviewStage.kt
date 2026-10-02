package ing.fuyaoskyrocket.photoinfo.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.domain.layout.EditorWorkspacePolicy
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoSpacing

/** All photo workspaces reserve the same viewport and separate action row. */
@Composable
fun PhotoPreviewStage(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    media: @Composable BoxScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        PhotoAmbientBackdrop(bitmap, featherEdges = false, modifier = Modifier.matchParentSize())
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
