package ing.fuyaoskyrocket.photoinfo.ui.designsystem

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding

val LocalPaneTopInset = staticCompositionLocalOf { 0.dp }
val LocalBottomNavigationInset = staticCompositionLocalOf { 0.dp }

/** Shared page geometry for metadata, settings and lens forms. Each pane owns its own scroll. */
@Composable
fun FuyaoAdaptivePage(
    padding: PaddingValues,
    contentUnderTopEdge: Boolean = false,
    leadingPaneWidth: Dp? = null,
    single: @Composable (Modifier) -> Unit,
    leading: @Composable (Modifier) -> Unit,
    trailing: @Composable (Modifier) -> Unit,
) {
    val activity = LocalActivity.current
    val layoutFlow: Flow<WindowLayoutInfo?> = remember(activity) {
        activity?.let { WindowInfoTracker.getOrCreate(it).windowLayoutInfo(it) } ?: emptyFlow()
    }
    val layout by layoutFlow.collectAsStateWithLifecycle(initialValue = null)
    val fold = layout?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull { it.isSeparating }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val outerPadding = if (contentUnderTopEdge) PaddingValues(
        start = padding.calculateStartPadding(direction), end = padding.calculateEndPadding(direction),
        bottom = padding.calculateBottomPadding()) else padding
    val navigationInset = if (outerPadding.calculateBottomPadding() > 0.dp) 0.dp else LocalBottomNavigationInset.current
    CompositionLocalProvider(LocalPaneTopInset provides if (contentUnderTopEdge) padding.calculateTopPadding() else 0.dp,
        LocalBottomNavigationInset provides navigationInset) {
    Box(Modifier.fillMaxSize().padding(outerPadding).consumeWindowInsets(padding).imePadding()) {
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { bounds = it.boundsInWindow() }) {
            val hinge = fold?.bounds
            val vertical = fold?.orientation == FoldingFeature.Orientation.VERTICAL && hinge != null &&
                hinge.left > bounds.left && hinge.right < bounds.right
            val horizontal = fold?.orientation == FoldingFeature.Orientation.HORIZONTAL && hinge != null &&
                hinge.top > bounds.top && hinge.bottom < bounds.bottom
            when {
                vertical -> {
                    val foldBounds = requireNotNull(hinge)
                    val left = with(density) { (foldBounds.left - bounds.left).toDp() }
                    val right = with(density) { (bounds.right - foldBounds.right).toDp() }
                    val gap = with(density) { foldBounds.width().toDp() }
                    val leadingWidth = if (direction == LayoutDirection.Ltr) left else right
                    val trailingWidth = if (direction == LayoutDirection.Ltr) right else left
                    if (density.fontScale <= 1.4f && leadingWidth >= 280.dp && trailingWidth >= 400.dp) Row(Modifier.fillMaxSize()) {
                        leading(Modifier.width(leadingWidth).fillMaxHeight())
                        Spacer(Modifier.width(gap))
                        trailing(Modifier.weight(1f).fillMaxHeight())
                    } else {
                        val useRight = right > left
                        single(Modifier.fillMaxSize().absolutePadding(
                            left = if (useRight) left + gap else 0.dp,
                            right = if (useRight) 0.dp else right + gap))
                    }
                }
                horizontal -> {
                    val foldBounds = requireNotNull(hinge)
                    val top = with(density) { (foldBounds.top - bounds.top).toDp() }
                    val bottom = with(density) { (bounds.bottom - foldBounds.bottom).toDp() }
                    val gap = with(density) { foldBounds.height().toDp() }
                    if (top >= 360.dp && bottom >= 360.dp) Column(Modifier.fillMaxSize()) {
                        leading(Modifier.fillMaxWidth().height(top))
                        Spacer(Modifier.height(gap))
                        CompositionLocalProvider(LocalPaneTopInset provides 0.dp) {
                            trailing(Modifier.fillMaxWidth().weight(1f))
                        }
                    } else {
                        val useBottom = bottom > top
                        CompositionLocalProvider(LocalPaneTopInset provides if (useBottom) 0.dp else LocalPaneTopInset.current) {
                            single(Modifier.fillMaxSize().absolutePadding(
                                top = if (useBottom) top + gap else 0.dp,
                                bottom = if (useBottom) 0.dp else bottom + gap))
                        }
                    }
                }
                maxWidth >= 840.dp && density.fontScale <= 1.4f -> {
                    val contentWidth = minOf(maxWidth, 1120.dp)
                    val leadingWidth = leadingPaneWidth?.coerceIn(240.dp, contentWidth - 400.dp) ?: contentWidth * .4f
                    Row(Modifier.widthIn(max = 1120.dp).fillMaxWidth().fillMaxHeight()
                        .align(Alignment.TopCenter)) {
                        leading(Modifier.width(leadingWidth).fillMaxHeight())
                        trailing(Modifier.weight(1f).fillMaxHeight())
                    }
                }
                else -> single(Modifier.fillMaxSize())
            }
        }
    }
    }
}
