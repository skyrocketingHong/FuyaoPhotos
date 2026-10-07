package ing.fuyaoskyrocket.photoinfo.features.colors.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.PhotoColorInfo
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.SampledColor
import ing.fuyaoskyrocket.photoinfo.ui.components.CyclicItemSelector
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.*

@Composable
internal fun ColorResultPanel(bitmap: Bitmap?, sampledColor: SampledColor?, photoColorInfo: PhotoColorInfo?,
    modifier: Modifier = Modifier, sourceProfile: String? = photoColorInfo?.colorSpaceName) {
    var group by rememberSaveable { mutableIntStateOf(0) }
    var valueIndex by rememberSaveable { mutableIntStateOf(0) }
    var referenceIndex by rememberSaveable { mutableIntStateOf(0) }
    val valueLabels = listOf(stringResource(R.string.colors_auto_space,
        sampledColor?.sourceColorSpaceName ?: photoColorInfo?.colorSpaceName ?: sourceProfile.orEmpty())) +
        ColorValueTab.entries.take(7).map { stringResource(it.labelResource) }
    val referenceLabels = ColorValueTab.entries.drop(7).map { stringResource(it.labelResource) }
    val labels = if (group == 0) valueLabels else referenceLabels
    val selected = if (group == 0) valueIndex else referenceIndex
    val selectedTab = if (group == 1) referenceIndex + 7 else valueIndex - 1
    val select: (Int) -> Unit = { if (group == 0) valueIndex = it else referenceIndex = it }

    BoxWithConstraints(modifier) {
        val compact = maxHeight - LocalBottomNavigationInset.current < 240.dp || maxWidth < 320.dp || LocalDensity.current.fontScale > 1.6f
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            CurrentPixelSummary(bitmap, sampledColor, compact,
                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = FuyaoSpacing.content))
            if (!compact) FuyaoPrimaryTabs(listOf(stringResource(R.string.colors_values), stringResource(R.string.colors_references)),
                group, { group = it })
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().weight(1f).padding(horizontal = FuyaoSpacing.content),
                horizontalArrangement = Arrangement.spacedBy(FuyaoSpacing.compact)) {
                Box(Modifier.weight(.34f).fillMaxHeight().padding(bottom = LocalBottomNavigationInset.current),
                    contentAlignment = Alignment.Center) {
                    if (compact) ColorChoiceMenu(valueLabels, referenceLabels, group, selected) { nextGroup, index ->
                        group = nextGroup
                        if (nextGroup == 0) valueIndex = index else referenceIndex = index
                    }
                    else key(group) { CyclicItemSelector(labels, selected, true, Modifier.fillMaxSize(), select) }
                }
                FuyaoPageColumn(Modifier.weight(.66f).fillMaxHeight(), topInset = 0.dp, horizontalPadding = 0.dp) {
                    if (selectedTab < 0) SourceColorContent(sampledColor)
                    else ColorValueContent(selectedTab, sampledColor)
                    SampleSummary(sampledColor, photoColorInfo)
                }
            }
        }
    }
}

@Composable
private fun ColorChoiceMenu(values: List<String>, references: List<String>, group: Int, selected: Int,
    onSelect: (Int, Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labels = if (group == 0) values else references
    Box {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) {
            Text(labels[selected], style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(expanded, { expanded = false }) {
            listOf(values, references).forEachIndexed { nextGroup, items ->
                if (nextGroup > 0) HorizontalDivider()
                Text(stringResource(if (nextGroup == 0) R.string.colors_values else R.string.colors_references),
                    Modifier.padding(FuyaoSpacing.compact), style = MaterialTheme.typography.labelLarge)
                items.forEachIndexed { index, label ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; onSelect(nextGroup, index) })
                }
            }
        }
    }
}

@Composable
private fun SampleSummary(sample: SampledColor?, info: PhotoColorInfo?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FuyaoSpacing.small)) {
        val items = buildList {
            if (info != null) {
                add(ColorValueItem(stringResource(R.string.cp_sample_relative_position_label), sample?.let {
                    val x = if (info.width > 1) it.sourceX * 100.0 / (info.width - 1) else 0.0
                    val y = if (info.height > 1) it.sourceY * 100.0 / (info.height - 1) else 0.0
                    stringResource(R.string.cp_sample_relative_position, x, y)
                }))
                add(ColorValueItem(stringResource(R.string.cp_sample_image_summary_label),
                    stringResource(R.string.cp_sample_image_summary, info.width, info.height,
                        sample?.sourceColorSpaceName ?: info.colorSpaceName), fullWidth = true))
            }
        }
        ColorValuePanel(items)
    }
}

@Composable
private fun CurrentPixelSummary(bitmap: Bitmap?, sample: SampledColor?, compact: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = FuyaoSpacing.small), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FuyaoSpacing.compact)) {
        SamplingMagnifier(bitmap, sample, stringResource(R.string.cp_sampling_magnifier_description),
            Modifier.size(if (compact) 40.dp else 56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FuyaoSpacing.xs)) {
            Text(stringResource(R.string.colors_current_pixel), style = MaterialTheme.typography.labelLarge)
            Text(sample?.let { stringResource(R.string.cp_sample_coordinates, it.sourceX, it.sourceY) }
                ?: stringResource(R.string.colors_value_unavailable), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
