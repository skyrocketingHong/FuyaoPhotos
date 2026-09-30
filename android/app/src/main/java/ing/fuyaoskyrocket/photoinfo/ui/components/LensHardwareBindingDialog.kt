package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.lens.HardwareLens
import ing.fuyaoskyrocket.photoinfo.domain.lens.LensBindings
import ing.fuyaoskyrocket.photoinfo.domain.lens.LensProfile

@Composable
fun LensHardwareBindingDialog(profile: LensProfile, profiles: List<LensProfile>, hardware: List<HardwareLens>,
                              hardwareDevice: String, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.lens_hardware_bind)) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(profile.device)
            Text(profile.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.lens_hardware_manual_description))
            if (hardware.isEmpty()) Text(stringResource(R.string.lens_hardware_empty))
            hardware.forEach { lens ->
                val occupied = profiles.any { it.id != profile.id && it.boundTo(hardwareDevice, lens.id) }
                val compatible = LensBindings.compatible(profile, lens, false)
                TextButton(onClick = { onSelect(lens.id) }, enabled = !occupied && compatible, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.hardware_identity, stringResource(when (lens.facing) {
                            "BACK" -> R.string.lens_back; "FRONT" -> R.string.lens_front; else -> R.string.lens_external
                        }), lens.id))
                        Text(stringResource(R.string.hardware_values, lens.physicalFocals.joinToString(), lens.apertures.joinToString()),
                            style = MaterialTheme.typography.bodySmall)
                        if (occupied) Text(stringResource(R.string.lens_hardware_occupied), style = MaterialTheme.typography.bodySmall)
                        else if (!compatible) Text(stringResource(R.string.lens_hardware_mismatch), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (profile.cameraId.isNotBlank()) TextButton(onClick = { onSelect(null) }) { Text(stringResource(R.string.lens_hardware_unbind)) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}
