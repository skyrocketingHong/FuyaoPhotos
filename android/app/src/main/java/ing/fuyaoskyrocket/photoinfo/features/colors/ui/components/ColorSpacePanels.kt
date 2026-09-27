package ing.fuyaoskyrocket.photoinfo.features.colors.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.SampledColor

@Composable
internal fun SrgbContent(sampledColor: SampledColor?, modifier: Modifier = Modifier) {
    val values = sampledColor?.representations
    ColorValuePanel(
        items = listOf(
            ColorValueItem(stringResource(R.string.cp_color_value_hex), sampledColor?.sRgb?.hex),
            ColorValueItem(stringResource(R.string.cp_color_value_rgb), sampledColor?.sRgb?.rgbComponents),
            ColorValueItem(stringResource(R.string.cp_color_value_hsl), values?.hsl?.componentsText),
            ColorValueItem(stringResource(R.string.cp_color_value_cmyk), values?.cmyk?.componentsText),
            ColorValueItem(
                stringResource(R.string.cp_css_rgb_function_label),
                sampledColor?.sRgb?.let { "rgb(${it.red} ${it.green} ${it.blue})" },
                fullWidth = true,
            ),
            ColorValueItem(
                stringResource(R.string.cp_css_hsl_function_label),
                values?.hsl?.cssText,
                fullWidth = true,
            ),
            ColorValueItem(
                stringResource(R.string.cp_css_color_function_label),
                sampledColor?.sRgb?.cssColorFunction("srgb"),
                fullWidth = true,
            ),
        ),
        modifier = modifier,
    )
}

@Composable
internal fun DisplayP3Content(sampledColor: SampledColor?, modifier: Modifier = Modifier) {
    ColorValuePanel(
        items = listOf(
            ColorValueItem(stringResource(R.string.cp_color_value_hex), sampledColor?.displayP3?.hex),
            ColorValueItem(stringResource(R.string.cp_color_value_rgb), sampledColor?.displayP3?.rgbComponents),
            ColorValueItem(
                stringResource(R.string.cp_css_color_function_label),
                sampledColor?.displayP3?.cssColorFunction("display-p3"),
                fullWidth = true,
            ),
        ),
        modifier = modifier,
    )
}

@Composable
internal fun Bt2020Content(sampledColor: SampledColor?, modifier: Modifier = Modifier) {
    ColorValuePanel(
        items = listOf(
            ColorValueItem(stringResource(R.string.cp_color_value_hex), sampledColor?.bt2020?.hex),
            ColorValueItem(stringResource(R.string.cp_color_value_rgb), sampledColor?.bt2020?.rgbComponents),
            ColorValueItem(
                stringResource(R.string.cp_css_color_function_label),
                sampledColor?.representations?.cssRec2020?.cssText("rec2020"),
                fullWidth = true,
            ),
        ),
        modifier = modifier,
    )
}

@Composable
internal fun A98RgbContent(sampledColor: SampledColor?, modifier: Modifier = Modifier) {
    ColorValuePanel(
        items = listOf(
            ColorValueItem(stringResource(R.string.cp_color_value_hex), sampledColor?.adobeRgb?.hex),
            ColorValueItem(stringResource(R.string.cp_color_value_rgb), sampledColor?.adobeRgb?.rgbComponents),
            ColorValueItem(
                stringResource(R.string.cp_css_color_function_label),
                sampledColor?.representations?.cssA98Rgb?.cssText("a98-rgb"),
                fullWidth = true,
            ),
        ),
        modifier = modifier,
    )
}

@Composable
internal fun CieContent(sampledColor: SampledColor?, modifier: Modifier = Modifier) {
    val values = sampledColor?.representations
    ColorValuePanel(
        items = listOf(
            ColorValueItem(stringResource(R.string.cp_cie_lab_value_label), values?.cieLab?.componentsText),
            ColorValueItem(stringResource(R.string.cp_cie_lch_value_label), values?.cieLch?.componentsText),
            ColorValueItem(
                stringResource(R.string.cp_css_lab_function_label),
                values?.cieLab?.cssText,
                fullWidth = true,
            ),
            ColorValueItem(
                stringResource(R.string.cp_css_lch_function_label),
                values?.cieLch?.cssText,
                fullWidth = true,
            ),
        ),
        modifier = modifier,
    )
}

@Composable
internal fun OkColorContent(sampledColor: SampledColor?, modifier: Modifier = Modifier) {
    val values = sampledColor?.representations
    ColorValuePanel(
        items = listOf(
            ColorValueItem(stringResource(R.string.cp_oklab_value_label), values?.okLab?.componentsText),
            ColorValueItem(stringResource(R.string.cp_oklch_value_label), values?.okLch?.componentsText),
            ColorValueItem(
                stringResource(R.string.cp_css_oklab_function_label),
                values?.okLab?.cssText,
                fullWidth = true,
            ),
            ColorValueItem(
                stringResource(R.string.cp_css_oklch_function_label),
                values?.okLch?.cssText,
                fullWidth = true,
            ),
        ),
        modifier = modifier,
    )
}

@Composable
internal fun XyzContent(sampledColor: SampledColor?, modifier: Modifier = Modifier) {
    val values = sampledColor?.representations
    ColorValuePanel(
        items = listOf(
            ColorValueItem(stringResource(R.string.cp_xyz_d50_label), values?.xyzD50?.componentsText),
            ColorValueItem(stringResource(R.string.cp_xyz_d65_label), values?.xyzD65?.componentsText),
            ColorValueItem(
                stringResource(R.string.cp_css_color_function_label),
                values?.xyzD50?.cssText("xyz-d50"),
                fullWidth = true,
            ),
            ColorValueItem(
                stringResource(R.string.cp_css_color_function_label),
                values?.xyzD65?.cssText("xyz-d65"),
                fullWidth = true,
            ),
        ),
        modifier = modifier,
    )
}
