package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import android.os.Build
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
import ing.fuyaoskyrocket.photoinfo.platform.ImageEncoderSupport

val ExportOptionsSaver = listSaver<ExportOptions, String>(save={ it.fields() }, restore={ ExportOptions.restore(it) })

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ExportOptionsControls(options: ExportOptions, onChange: (ExportOptions) -> Unit, jpegRequired: Boolean = false,
    hasMotion: Boolean = false, hasPortrait: Boolean = false, showLiveOption: Boolean = true, showPortraitOption: Boolean = true,
    avifRequired: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val supportedFormats=remember { ExportFormat.entries.filter(ImageEncoderSupport::supports) }
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(options.format.name, {}, readOnly = true, label = { Text(stringResource(R.string.export_format)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
        ExposedDropdownMenu(expanded, { expanded = false }) {
            ExportFormat.entries.forEach { format ->
                DropdownMenuItem(text = { Text(format.name) },
                    enabled = (!jpegRequired || format != ExportFormat.PNG) &&
                        format in supportedFormats &&
                        (!avifRequired || format==ExportFormat.AVIF) &&
                        (!hasPortrait || format==(if(options.applePortrait)ExportFormat.HEIC else ExportFormat.JPEG)) &&
                        (!options.appleStyle || format==ExportFormat.HEIC) &&
                        (!hasMotion || format==ExportFormat.JPEG || format==ExportFormat.HEIC) &&
                        (format != ExportFormat.HEIC || Build.VERSION.SDK_INT >= 28) &&
                        (format != ExportFormat.AVIF || Build.VERSION.SDK_INT >= 34) &&
                        (!avifRequired || format==ExportFormat.AVIF || format==ExportFormat.HEIC),
                    onClick = { onChange(options.copy(format = format)); expanded = false })
            }
        }
    }
    if(options.format !in supportedFormats) Text(stringResource(R.string.image_encoder_unavailable),
        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
    if(showLiveOption) MetadataSwitch(R.string.live_pair,R.string.live_pair_hint,options.separateLivePhoto) {
        onChange(options.copy(separateLivePhoto=it))
    }
    if(showPortraitOption && Build.VERSION.SDK_INT>=34 && ExportFormat.HEIC in supportedFormats) MetadataSwitch(R.string.apple_portrait,R.string.apple_portrait_hint,options.applePortrait) {
        onChange(options.copy(applePortrait=it,
            format=if(it)ExportFormat.HEIC else if(hasPortrait)ExportFormat.JPEG else options.format))
    }
    if(ExportFormat.HEIC in supportedFormats && Build.VERSION.SDK_INT>=28) MetadataSwitch(R.string.apple_style,R.string.apple_style_hint,options.appleStyle) {
        // HEIC cannot carry the Xiaomi tail, so enabling the style pulls in the depth
        // conversion when the photo has one; motion stays embedded either way.
        onChange(options.copy(appleStyle=it,
            appleStyle3=it && options.appleStyle3,
            applePortrait=options.applePortrait || (it && hasPortrait),
            format=if(it)ExportFormat.HEIC else options.format))
    }
    if(options.appleStyle && ExportFormat.HEIC in supportedFormats && Build.VERSION.SDK_INT>=28)
        MetadataSwitch(R.string.apple_style3,R.string.apple_style3_hint,options.appleStyle3) {
            // The native contract requires the 2023 styles item to coexist with
            // texture styles, so enabling it pulls the plain style along.
            onChange(options.copy(appleStyle3=it, appleStyle=it || options.appleStyle,
                applePortrait=options.applePortrait || (it && hasPortrait),
                format=if(it)ExportFormat.HEIC else options.format))
        }
    if(options.format==ExportFormat.HEIC) Text(stringResource(R.string.heic_sdr_hint),
        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(options.format==ExportFormat.HEIC && hasMotion && !options.separateLivePhoto) Text(stringResource(R.string.heic_motion_hint),
        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(options.format!=ExportFormat.PNG) {
        val label=stringResource(R.string.encoding_quality)
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
