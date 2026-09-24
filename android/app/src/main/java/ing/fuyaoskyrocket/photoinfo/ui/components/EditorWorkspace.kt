package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
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
                    val photoWidth = if (direction == LayoutDirection.Ltr) left else right
                    val controlsWidth = if (direction == LayoutDirection.Ltr) right else left
                    if (!EditorWorkspacePolicy.canSplitAcrossFold(photoWidth.value, controlsWidth.value, density.fontScale)) {
                        val useRight = right > left
                        BoxWithConstraints(Modifier.fillMaxSize().absolutePadding(
                            left = if (useRight) left + gap else 0.dp,
                            right = if (useRight) 0.dp else right + gap,
                        )) {
                            AdaptiveEditorRegions(maxWidth.value, maxHeight.value, density.fontScale,
                                imeVisible, preview, controls)
                        }
                    } else Row(Modifier.fillMaxSize()) {
                        preview(Modifier.width(photoWidth).fillMaxHeight(), true)
                        Spacer(Modifier.width(gap))
                        controls(Modifier.weight(1f).fillMaxHeight())
                    }
                }
                horizontalFold -> {
                    val top = with(density) { (foldBounds.top - bounds.top).toDp() }
                    val bottom = with(density) { (bounds.bottom - foldBounds.bottom).toDp() }
                    val gap = with(density) { foldBounds.height().toDp() }
                    if (top < 120.dp || bottom < 180.dp) {
                        val useBottom = bottom > top
                        BoxWithConstraints(Modifier.fillMaxSize().absolutePadding(
                            top = if (useBottom) top + gap else 0.dp,
                            bottom = if (useBottom) 0.dp else bottom + gap,
                        )) {
                            AdaptiveEditorRegions(maxWidth.value, maxHeight.value, density.fontScale,
                                imeVisible, preview, controls)
                        }
                    } else Column(Modifier.fillMaxSize()) {
                        preview(Modifier.fillMaxWidth().height(top), false)
                        Spacer(Modifier.height(gap))
                        controls(Modifier.fillMaxWidth().weight(1f))
                    }
                }
                else -> AdaptiveEditorRegions(maxWidth.value, maxHeight.value, density.fontScale,
                    imeVisible, preview, controls)
            }
        }
    }
}

@Composable
private fun AdaptiveEditorRegions(
    width: Float,
    height: Float,
    fontScale: Float,
    imeVisible: Boolean,
    preview: @Composable (Modifier, Boolean) -> Unit,
    controls: @Composable (Modifier) -> Unit,
) {
    val spec = EditorWorkspacePolicy.calculate(width, height, fontScale, imeVisible)
    if (spec.sideBySide) Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(EditorWorkspacePolicy.PANE_GAP.dp)) {
        preview(Modifier.weight(1f).fillMaxHeight(), true)
        controls(Modifier.width(spec.inspectorWidth.dp).fillMaxHeight())
    } else {
        val previewHeight by animateDpAsState(
            targetValue = spec.previewHeight.dp,
            animationSpec = tween(durationMillis = 220),
            label = "editor photo region",
        )
        Column(Modifier.fillMaxSize()) {
            preview(Modifier.fillMaxWidth().height(previewHeight), false)
            controls(Modifier.fillMaxWidth().weight(1f))
        }
    }
}
