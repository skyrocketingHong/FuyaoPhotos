package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CardStyleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    referenceValue: Float,
    label: String,
    valueText: String,
    referenceText: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val markerOuter = MaterialTheme.colorScheme.onSurface
    val markerInner = MaterialTheme.colorScheme.surface
    val referenceFraction = ((referenceValue - valueRange.start) /
        (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.CenterEnd) {
            Text(valueText, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_minus), null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).semantics { contentDescription = label },
                enabled = enabled,
                valueRange = valueRange,
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        enabled = enabled,
                        drawStopIndicator = null,
                        modifier = Modifier.drawWithContent {
                            drawContent()
                            val x = if (layoutDirection == LayoutDirection.Rtl) 1f - referenceFraction else referenceFraction
                            val center = Offset(size.width * x, size.height / 2f)
                            drawCircle(markerOuter, radius = 4.dp.toPx(), center = center)
                            drawCircle(markerInner, radius = 2.dp.toPx(), center = center)
                        },
                    )
                },
            )
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(R.drawable.ic_plus), null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterEnd) {
            Text(stringResource(R.string.style_reference, referenceText),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
