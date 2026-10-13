package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.layout.EditorActionStripPolicy
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoIconButton

internal enum class EditorTool(val priority: Int, val afterPrimary: Boolean = false) {
    MOTION(4), HDR(3), ORIGINAL(1), CLOSE(0, true), FULLSCREEN(2, true),
}

internal data class EditorToolAction(
    val tool: EditorTool,
    @DrawableRes val icon: Int,
    val label: String,
    val enabled: Boolean,
    val checked: Boolean? = null,
    val onClick: () -> Unit,
)

@Composable
internal fun EditorActionStrip(
    tools: List<EditorToolAction>,
    primaryActions: @Composable RowScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val layout = EditorActionStripPolicy.calculate(maxWidth.value, tools.size)
        val inlineTools = tools.sortedBy { it.tool.priority }.take(layout.inlineToolCount).map { it.tool }.toSet()
        val overflowTools = tools.filter { it.tool !in inlineTools }
        val overflowEnabled = overflowTools.any { it.enabled }
        var menuOpen by remember { mutableStateOf(false) }
        val scroll = rememberScrollState()
        LaunchedEffect(overflowEnabled) { if (!overflowEnabled) menuOpen = false }
        @Composable fun action(item: EditorToolAction) {
            if (item.checked != null) PreviewMediaButton(item.icon, item.label, item.checked, item.onClick, item.enabled)
            else FuyaoIconButton(item.icon, item.label, item.onClick, item.enabled)
        }
        Row(
            Modifier.then(if (layout.scrolls) Modifier.horizontalScroll(scroll) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(EditorActionStripPolicy.SPACING.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tools.filter { it.tool in inlineTools && !it.tool.afterPrimary }.forEach { item ->
                key(item.tool) { action(item) }
            }
            primaryActions()
            tools.filter { it.tool in inlineTools && it.tool.afterPrimary }.forEach { item ->
                key(item.tool) { action(item) }
            }
            if (layout.hasOverflow) Box {
                FuyaoIconButton(R.drawable.ic_more, stringResource(R.string.photo_tools),
                    { menuOpen = true }, enabled = overflowEnabled)
                DropdownMenu(menuOpen && overflowEnabled, { menuOpen = false }) {
                    overflowTools.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label) },
                            leadingIcon = { Icon(painterResource(item.icon), null, Modifier.size(24.dp)) },
                            trailingIcon = if (item.checked == null) null else {
                                { Checkbox(item.checked, onCheckedChange = null, enabled = item.enabled) }
                            },
                            enabled = item.enabled,
                            onClick = { menuOpen = false; item.onClick() },
                        )
                    }
                }
            }
        }
    }
}
