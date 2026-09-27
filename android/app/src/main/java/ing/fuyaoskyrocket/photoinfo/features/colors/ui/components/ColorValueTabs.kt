package ing.fuyaoskyrocket.photoinfo.features.colors.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.SampledColor
import ing.fuyaoskyrocket.photoinfo.features.colors.ui.theme.FuyaoDimensions

private enum class ColorValueTab(@StringRes val labelResource: Int) {
    SRGB(R.string.cp_color_tab_srgb),
    DISPLAY_P3(R.string.cp_color_tab_display_p3),
    BT2020(R.string.cp_color_tab_bt2020),
    A98_RGB(R.string.cp_color_tab_a98_rgb),
    CIE(R.string.cp_color_tab_cie),
    OKLAB(R.string.cp_color_tab_oklab),
    XYZ(R.string.cp_color_tab_xyz),
    CSS(R.string.cp_color_tab_css),
    RAL(R.string.cp_color_tab_ral),
}

/** Compact Material 3 secondary tabs placed beside the fixed magnifier. */
@Composable
internal fun ColorValueTabRow(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    autoSpaceIndex: Int = 0,
) {
    val tabs = ColorValueTab.entries
    val safeSelectedIndex = if (selectedTabIndex < 0) 0 else selectedTabIndex.coerceIn(tabs.indices) + 1
    SecondaryScrollableTabRow(
        selectedTabIndex = safeSelectedIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Transparent,
        edgePadding = 0.dp,
        divider = {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Tab(selected = selectedTabIndex < 0, onClick = { onTabSelected(-1) }, text = {
            Text(stringResource(R.string.colors_auto_space, stringResource(tabs[autoSpaceIndex.coerceIn(tabs.indices)].labelResource)))
        })
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = stringResource(tab.labelResource),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

/** Fixed-height values for the selected color-space tab. */
@Composable
internal fun ColorValueContent(
    selectedTabIndex: Int,
    sampledColor: SampledColor?,
    modifier: Modifier = Modifier,
) {
    val selectedTab = ColorValueTab.entries.getOrElse(selectedTabIndex) {
        ColorValueTab.SRGB
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FuyaoDimensions.colorValuesContentMinHeight),
    ) {
        when (selectedTab) {
            ColorValueTab.SRGB -> SrgbContent(sampledColor)
            ColorValueTab.DISPLAY_P3 -> DisplayP3Content(sampledColor)
            ColorValueTab.BT2020 -> Bt2020Content(sampledColor)
            ColorValueTab.A98_RGB -> A98RgbContent(sampledColor)
            ColorValueTab.CIE -> CieContent(sampledColor)
            ColorValueTab.OKLAB -> OkColorContent(sampledColor)
            ColorValueTab.XYZ -> XyzContent(sampledColor)
            ColorValueTab.CSS -> CssNamedColorContent(sampledColor)
            ColorValueTab.RAL -> RalValueContent(sampledColor)
        }
    }
}
