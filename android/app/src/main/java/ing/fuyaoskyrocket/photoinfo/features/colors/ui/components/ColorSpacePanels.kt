package ing.fuyaoskyrocket.photoinfo.features.colors.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.SampledColor
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoSpacing

@Composable
internal fun SourceColorContent(sampledColor: SampledColor?, modifier: Modifier = Modifier) {
    val value = sampledColor?.sourceRgb
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FuyaoSpacing.small)) {
        ColorValuePanel(buildList {
            add(ColorValueItem(stringResource(R.string.colors_source_profile), sampledColor?.sourceColorSpaceName, true))
            add(ColorValueItem(stringResource(R.string.colors_source_components), value?.normalizedComponents, true))
            add(ColorValueItem(stringResource(R.string.cp_color_value_rgb), value?.rgbComponents))
            add(ColorValueItem(stringResource(R.string.cp_color_value_hex), value?.hex))
            sampledColor?.sourceCssSpace?.let { cssSpace ->
                add(ColorValueItem(stringResource(R.string.cp_css_color_function_label), value?.cssColorFunction(cssSpace), true))
            }
        })
        Text(stringResource(R.string.colors_source_values_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (value?.wasClamped == true) ColorClippingNote()
    }
}

@Composable
private fun ColorClippingNote() {
    Text(stringResource(R.string.colors_clipped_values_note), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun SrgbContent(sampledColor: SampledColor?, modifier: Modifier = Modifier) {
    val values = sampledColor?.representations
    ColorValuePanel(
        items = listOf(
            ColorValueItem(stringResource(R.string.cp_color_value_hex), sampledColor?.sRgb?.hex),
            ColorValueItem(stringResource(R.string.cp_color_value_rgb), sampledColor?.sRgb?.rgbComponents),
            ColorValueItem(stringResource(R.string.colors_converted_components), sampledColor?.sRgb?.normalizedComponents, true),
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
    if (sampledColor?.sRgb?.wasClamped == true) ColorClippingNote()
    Text(stringResource(R.string.colors_cmyk_approximation_note), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun DisplayP3Content(sampledColor: SampledColor?, modifier: Modifier = Modifier) {
    ColorValuePanel(
        items = listOf(
            ColorValueItem(stringResource(R.string.cp_color_value_hex), sampledColor?.displayP3?.hex),
            ColorValueItem(stringResource(R.string.cp_color_value_rgb), sampledColor?.displayP3?.rgbComponents),
            ColorValueItem(stringResource(R.string.colors_converted_components), sampledColor?.displayP3?.normalizedComponents, true),
            ColorValueItem(
                stringResource(R.string.cp_css_color_function_label),
                sampledColor?.displayP3?.cssColorFunction("display-p3"),
                fullWidth = true,
            ),
        ),
        modifier = modifier,
    )
    if (sampledColor?.displayP3?.wasClamped == true) ColorClippingNote()
}

@Composable
internal fun Bt2020Content(sampledColor: SampledColor?, modifier: Modifier = Modifier) {
    ColorValuePanel(
        items = listOf(
            ColorValueItem(stringResource(R.string.cp_color_value_hex), sampledColor?.bt2020?.hex),
            ColorValueItem(stringResource(R.string.cp_color_value_rgb), sampledColor?.bt2020?.rgbComponents),
            ColorValueItem(stringResource(R.string.colors_converted_components), sampledColor?.bt2020?.normalizedComponents, true),
            ColorValueItem(
                stringResource(R.string.cp_css_color_function_label),
                sampledColor?.representations?.cssRec2020?.cssText("rec2020"),
                fullWidth = true,
            ),
        ),
        modifier = modifier,
    )
    Text(stringResource(R.string.colors_rec2020_encoding_note), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (sampledColor?.bt2020?.wasClamped == true) ColorClippingNote()
}

@Composable
internal fun A98RgbContent(sampledColor: SampledColor?, modifier: Modifier = Modifier) {
    ColorValuePanel(
        items = listOf(
            ColorValueItem(stringResource(R.string.cp_color_value_hex), sampledColor?.adobeRgb?.hex),
            ColorValueItem(stringResource(R.string.cp_color_value_rgb), sampledColor?.adobeRgb?.rgbComponents),
            ColorValueItem(stringResource(R.string.colors_converted_components), sampledColor?.adobeRgb?.normalizedComponents, true),
            ColorValueItem(
                stringResource(R.string.cp_css_color_function_label),
                sampledColor?.representations?.cssA98Rgb?.cssText("a98-rgb"),
                fullWidth = true,
            ),
        ),
        modifier = modifier,
    )
    if (sampledColor?.adobeRgb?.wasClamped == true) ColorClippingNote()
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
