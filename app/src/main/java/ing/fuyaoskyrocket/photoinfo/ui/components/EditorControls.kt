package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.model.CardStyle
import ing.fuyaoskyrocket.photoinfo.domain.model.FieldId
import ing.fuyaoskyrocket.photoinfo.presentation.LocationStatus
import ing.fuyaoskyrocket.photoinfo.presentation.EditorState
import kotlin.math.roundToInt

@Composable
fun EditorControls(
    state: EditorState, onField: (FieldId, String) -> Unit,
    onStyle: (CardStyle) -> Unit, onResetFields: () -> Unit,
    onImportFont: () -> Unit, onResetFont: () -> Unit, onResolveLocation: () -> Unit, modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val enabled = !state.busy
    Column(modifier) {
        PrimaryTabRow(selectedTabIndex = selected) {
            listOf(R.string.tab_info, R.string.tab_style).forEachIndexed { index, title ->
                Tab(selected = selected == index, onClick = { selected = index }, text = { Text(stringResource(title)) })
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected == 0) {
                Text(stringResource(R.string.field_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FieldId.entries.forEach { field ->
                    OutlinedTextField(
                        value = state.info[field], onValueChange = { onField(field, it) },
                        label = { Text(stringResource(fieldLabel(field))) },
                        modifier = Modifier.fillMaxWidth(), enabled = enabled && state.original != null,
                        singleLine = field !in setOf(FieldId.AUTHOR, FieldId.LOCATION, FieldId.CAMERA),
                        maxLines = if (field in setOf(FieldId.AUTHOR, FieldId.LOCATION, FieldId.CAMERA)) 3 else 1,
                    )
                    if (field == FieldId.LOCATION && state.original != null) {
                        val message = when (state.locationStatus) {
                            LocationStatus.RESOLVING -> R.string.location_resolving
                            LocationStatus.RESOLVED -> R.string.location_resolved
                            LocationStatus.UNAVAILABLE -> R.string.location_unavailable
                            LocationStatus.NO_GPS -> R.string.location_no_gps
                            LocationStatus.DISABLED -> R.string.location_disabled
                            LocationStatus.IDLE -> R.string.location_hint
                        }
                        Text(stringResource(message), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onResolveLocation,
                            enabled = enabled && state.hasPhotoGps && state.settings.resolvePhotoLocation && state.locationStatus != LocationStatus.RESOLVING) {
                            Text(stringResource(R.string.location_retry))
                        }
                    }
                }
                TextButton(onClick = onResetFields, enabled = enabled && state.original != null) {
                    Text(stringResource(R.string.restore_metadata))
                }
            } else {
                val s = state.style
                Text(stringResource(R.string.style_hint), style = MaterialTheme.typography.bodySmall)
                StyleSlider(stringResource(R.string.card_scale), "${(s.scale * 100).roundToInt()}%", s.scale, .6f..2f, enabled) { onStyle(s.copy(scale = it)) }
                StyleSlider(stringResource(R.string.text_scale), "${(s.textScale * 100).roundToInt()}%", s.textScale, .8f..1.8f, enabled) { onStyle(s.copy(textScale = it)) }
                Text(stringResource(R.string.text_scale_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                StyleSlider(stringResource(R.string.opacity), "${(s.opacity * 100).roundToInt()}%", s.opacity, 0f..1f, enabled) { onStyle(s.copy(opacity = it)) }
                StyleSlider(stringResource(R.string.blur), "${s.blur.roundToInt()} px", s.blur, 0f..50f, enabled) { onStyle(s.copy(blur = it)) }
                StyleSlider(stringResource(R.string.right_inset), "${s.rightInset.roundToInt()} px", s.rightInset, 0f..250f, enabled) { onStyle(s.copy(rightInset = it)) }
                StyleSlider(stringResource(R.string.bottom_inset), "${s.bottomInset.roundToInt()} px", s.bottomInset, 0f..250f, enabled) { onStyle(s.copy(bottomInset = it)) }
                StyleSlider(stringResource(R.string.radius), "${s.cornerRadius.roundToInt()} px", s.cornerRadius, 0f..40f, enabled) { onStyle(s.copy(cornerRadius = it)) }
                TextButton(onClick = { onStyle(CardStyle(textScale = 1f)) }, enabled = enabled) { Text(stringResource(R.string.reset_style)) }
                HorizontalDivider()
                Text(stringResource(R.string.font), style = MaterialTheme.typography.titleSmall)
                Text(state.fontName ?: stringResource(R.string.system_mono), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImportFont, enabled = enabled) { Text(stringResource(R.string.import_font)) }
                    TextButton(onClick = onResetFont, enabled = enabled && state.hasCustomFont) { Text(stringResource(R.string.reset)) }
                }
                Text(stringResource(R.string.font_license), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StyleSlider(label: String, display: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(display, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, enabled = enabled)
    }
}

private fun fieldLabel(field: FieldId) = when (field) {
    FieldId.DEVICE -> R.string.field_device
    FieldId.AUTHOR -> R.string.field_author
    FieldId.LOCATION -> R.string.field_location
    FieldId.CAMERA -> R.string.field_camera
    FieldId.IMAGE_SIZE -> R.string.field_size
    FieldId.FOCAL_LENGTH -> R.string.field_focal
    FieldId.EXPOSURE -> R.string.field_exposure
    FieldId.APERTURE -> R.string.field_aperture
    FieldId.ISO -> R.string.field_iso
}
