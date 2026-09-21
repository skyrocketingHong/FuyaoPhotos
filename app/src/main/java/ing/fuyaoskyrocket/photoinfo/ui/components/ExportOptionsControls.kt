package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportFormat
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportOptions
import kotlin.math.roundToInt

val ExportOptionsSaver = listSaver<ExportOptions, String>(save={ it.fields() }, restore={ ExportOptions.restore(it) })

@Composable
fun ExportOptionsControls(options: ExportOptions, onChange: (ExportOptions) -> Unit, jpegRequired: Boolean = false) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ExportFormat.entries.forEachIndexed { index, format ->
            SegmentedButton(selected=options.format==format, onClick={ onChange(options.copy(format=format)) },
                enabled=!jpegRequired || format==ExportFormat.JPEG,
                shape=SegmentedButtonDefaults.itemShape(index,2), modifier=Modifier.weight(1f)) {
                Text(stringResource(if(format==ExportFormat.JPEG)R.string.format_jpeg else R.string.format_png))
            }
        }
    }
    if(options.format==ExportFormat.JPEG) {
        val label=stringResource(R.string.jpeg_quality)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.value_percent,options.jpegQuality),style=MaterialTheme.typography.labelLarge)
        }
        Slider(options.jpegQuality.toFloat(),{ onChange(options.copy(jpegQuality=it.roundToInt())) },
            valueRange=0f..100f,steps=99,modifier=Modifier.fillMaxWidth().semantics { contentDescription=label })
    }
    MetadataSwitch(R.string.keep_metadata,R.string.keep_metadata_hint,options.keepExif) { onChange(options.copy(keepExif=it)) }
    MetadataSwitch(R.string.keep_location,R.string.keep_location_hint,options.keepLocation) { onChange(options.copy(keepLocation=it)) }
    MetadataSwitch(R.string.keep_capture_time,R.string.keep_capture_time_hint,options.keepCaptureTime) { onChange(options.copy(keepCaptureTime=it)) }
}

@Composable
private fun MetadataSwitch(label: Int, hint: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).toggleable(checked,role=Role.Switch,onValueChange=onChange),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(label),style=MaterialTheme.typography.bodyLarge)
            Text(stringResource(hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked,onCheckedChange=null)
    }
}
