package ing.fuyaoskyrocket.photoinfo.features.colors.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.SampledColor
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoSpacing
import java.util.Locale

@Composable
internal fun CssNamedColorContent(sampledColor: SampledColor?, modifier: Modifier = Modifier) {
    val match = sampledColor?.representations?.cssNamedColor
    val status = when {
        match == null -> stringResource(R.string.cp_css_name_keyword_label)
        match.isExact -> stringResource(R.string.cp_css_name_exact)
        else -> stringResource(
            R.string.cp_css_name_nearest,
            String.format(Locale.US, "%.4f", match.deltaEOk),
        )
    }
    val names = match?.names?.joinToString(" / ").orEmpty()
    ColorValuePanel(
        items = listOf(
            ColorValueItem(stringResource(R.string.cp_color_value_hex), match?.color?.hex),
            ColorValueItem(stringResource(R.string.cp_color_value_rgb), match?.color?.rgbComponents),
        ),
        modifier = modifier,
        header = {
            ReferenceColorHeader(
                color = match?.color?.argb?.let { Color(it) },
                title = names,
                subtitle = status,
            )
        },
    )
}

@Composable
internal fun RalValueContent(sampledColor: SampledColor?, modifier: Modifier = Modifier) {
    val match = sampledColor?.match
    ColorValuePanel(
        items = listOf(
            ColorValueItem(stringResource(R.string.cp_color_value_hex), match?.hex),
            ColorValueItem(stringResource(R.string.cp_color_value_rgb), match?.rgbComponents),
        ),
        modifier = modifier,
        header = {
            ReferenceColorHeader(
                color = match?.let { Color(it.red, it.green, it.blue) },
                title = match?.let { stringResource(R.string.cp_ral_code, it.code) }.orEmpty(),
                subtitle = match?.name.orEmpty(),
            )
        },
    )
}

@Composable
private fun ReferenceColorHeader(
    color: Color?,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FuyaoSpacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = MaterialTheme.shapes.medium
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(shape)
                .background(color ?: MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
