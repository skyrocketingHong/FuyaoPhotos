package ing.fuyaoskyrocket.photoinfo.ui.components

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import ing.fuyaoskyrocket.photoinfo.domain.model.WorkspaceSettings
import ing.fuyaoskyrocket.photoinfo.ui.components.liquid.LiquidBottomTab
import ing.fuyaoskyrocket.photoinfo.ui.components.liquid.LiquidBottomTabs

internal data class NavigationDestination(@StringRes val label: Int, @DrawableRes val icon: Int)

@Composable
internal fun FuyaoBottomNavigation(
    backdrop: Backdrop,
    settings: WorkspaceSettings,
    selectedIndex: Int,
    items: List<NavigationDestination>,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    val glass = settings.glassNavigation && Build.VERSION.SDK_INT >= 33 && LocalDensity.current.fontScale <= 1.4f
    if (glass) {
        Box(Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
            .padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            LiquidBottomTabs(selectedIndex, onSelect, backdrop, items.size,
                Modifier.widthIn(max = 600.dp).fillMaxWidth().selectableGroup(), settings.blurNavigation, enabled) {
                items.forEachIndexed { index, item ->
                    LiquidBottomTab(onClick = { onSelect(index) }, selected = selectedIndex == index, enabled = enabled) {
                        Icon(painterResource(item.icon), null, Modifier.size(24.dp))
                        Text(stringResource(item.label), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    } else {
        val surface = MaterialTheme.colorScheme.surfaceContainerLow
        val blurActive = settings.blurNavigation && Build.VERSION.SDK_INT >= 31
        val material = if (blurActive) Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = { blur(24.dp.toPx()) },
            highlight = null,
            shadow = null,
            onDrawSurface = { drawRect(surface.copy(alpha = .72f)) },
        ) else Modifier
        NavigationBar(modifier = material, containerColor = if (blurActive) Color.Transparent else surface,
            tonalElevation = 0.dp) {
            items.forEachIndexed { index, item ->
                NavigationBarItem(selectedIndex == index, onClick = { onSelect(index) }, enabled = enabled,
                    icon = { Icon(painterResource(item.icon), null, Modifier.size(24.dp)) },
                    label = { Text(stringResource(item.label)) })
            }
        }
    }
}
