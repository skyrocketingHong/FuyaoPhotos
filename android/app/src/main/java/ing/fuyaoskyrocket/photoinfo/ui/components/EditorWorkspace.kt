package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import ing.fuyaoskyrocket.photoinfo.domain.layout.EditorWorkspacePolicy
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Photo and inspector are two regions of one editing destination, not separate routes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorWorkspace(
    preview: @Composable (Modifier, Boolean) -> Unit,
    controls: @Composable (Modifier) -> Unit,
) {
    val activity = LocalActivity.current
    val layoutFlow: Flow<WindowLayoutInfo?> = remember(activity) {
        activity?.let { WindowInfoTracker.getOrCreate(it).windowLayoutInfo(it) }
            ?: emptyFlow()
    }
    val layout by layoutFlow.collectAsStateWithLifecycle(initialValue = null)
    val fold = layout?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull { it.isSeparating }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val imeVisible = WindowInsets.isImeVisible
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = FuyaoLayout.editor).fillMaxSize().onGloballyPositioned { bounds = it.boundsInWindow() }) {
            val foldBounds = fold?.bounds
            val verticalFold = fold?.orientation == FoldingFeature.Orientation.VERTICAL && foldBounds != null &&
                foldBounds.left > bounds.left && foldBounds.right < bounds.right
            val horizontalFold = fold?.orientation == FoldingFeature.Orientation.HORIZONTAL && foldBounds != null &&
                foldBounds.top > bounds.top && foldBounds.bottom < bounds.bottom
            when {
                verticalFold -> {
                    val left = with(density) { (foldBounds.left - bounds.left).toDp() }
                    val right = with(density) { (bounds.right - foldBounds.right).toDp() }
                    val gap = with(density) { foldBounds.width().toDp() }
                    if (minOf(left,right) < 240.dp) {
                        Box(Modifier.fillMaxSize().absolutePadding(
                            left=if(left<right)left+gap else 0.dp,
                            right=if(left<right)0.dp else right+gap)) {
                            Column(Modifier.fillMaxSize()) {
                                preview(Modifier.fillMaxWidth().height(52.dp),false)
                                controls(Modifier.fillMaxWidth().weight(1f))
                            }
                        }
                    } else Row(Modifier.fillMaxSize()) {
                        preview(Modifier.width(if (direction == LayoutDirection.Ltr) left else right).fillMaxHeight(), true)
                        Spacer(Modifier.width(gap))
                        controls(Modifier.weight(1f).fillMaxHeight())
                    }
                }
                horizontalFold -> {
                    val top = with(density) { (foldBounds.top - bounds.top).toDp() }
                    val gap = with(density) { foldBounds.height().toDp() }
                    Column(Modifier.fillMaxSize()) {
                        preview(Modifier.fillMaxWidth().height(top), false)
                        Spacer(Modifier.height(gap))
                        controls(Modifier.fillMaxWidth().weight(1f))
                    }
                }
                else -> {
                    val spec = EditorWorkspacePolicy.calculate(maxWidth.value, maxHeight.value, density.fontScale, imeVisible)
                    if (spec.sideBySide) Row(Modifier.fillMaxSize()) {
                        preview(Modifier.weight(1f).fillMaxHeight(), true)
                        VerticalDivider()
                        controls(Modifier.width(spec.inspectorWidth.dp).fillMaxHeight())
                    } else Column(Modifier.fillMaxSize()) {
                        preview(Modifier.fillMaxWidth().height(maxOf(52f,spec.previewHeight).dp), false)
                        controls(Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }
}
