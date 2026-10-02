package ing.fuyaoskyrocket.photoinfo.features.colors.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.SampledColor

internal enum class ColorValueTab(@StringRes val labelResource: Int) {
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

@Composable
internal fun ColorValueContent(selectedTabIndex: Int, sampledColor: SampledColor?) {
    when (ColorValueTab.entries[selectedTabIndex]) {
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
