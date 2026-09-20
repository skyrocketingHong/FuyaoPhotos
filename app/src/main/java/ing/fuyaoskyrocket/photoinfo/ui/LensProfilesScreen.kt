package ing.fuyaoskyrocket.photoinfo.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.data.camera.*
import ing.fuyaoskyrocket.photoinfo.domain.lens.LensProfile
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LensProfilesScreen(initial: List<LensProfile>, deviceHint: String = "", onBack: () -> Unit, onSave: (List<LensProfile>) -> Unit) {
    var profiles by remember { mutableStateOf(initial) }
    var inventory by remember { mutableStateOf<CameraInventory?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LensProfile?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun scan() { scope.launch {
        scanning = true; failed = false
        inventory = withContext(Dispatchers.IO) { runCatching { CameraInventoryReader(context).scan() }.getOrNull() }
        failed = inventory == null; scanning = false
    } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) scan() else failed = true }
    fun draft(hardware: HardwareLens? = null) = LensProfile(UUID.randomUUID().toString(), deviceHint.ifBlank { "${Build.MANUFACTURER} ${Build.MODEL}" }, "",
        hardware?.id.orEmpty(), 0.0, 0.0, physicalMin = hardware?.physicalFocals?.minOrNull(), physicalMax = hardware?.physicalFocals?.maxOrNull())
    BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.lens_profiles)) },
        navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
        actions = { TextButton(onClick = { onSave(profiles) }) { Text(stringResource(R.string.save)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.inventory_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { permission.launch(Manifest.permission.CAMERA) }, enabled = !scanning) { Text(stringResource(R.string.scan_lenses)) }
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Text(stringResource(R.string.inventory_failed), color = MaterialTheme.colorScheme.error)
            inventory?.let { result ->
                Text(stringResource(R.string.inventory_count, result.lenses.size, result.logicalCount))
                if (result.incomplete) Text(stringResource(R.string.inventory_incomplete), style = MaterialTheme.typography.bodySmall)
                result.lenses.forEach { hardware ->
                    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text("${hardware.facing} · ID ${hardware.id}")
                        Text(stringResource(R.string.hardware_values, hardware.physicalFocals.joinToString(), hardware.apertures.joinToString()), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { editing = draft(hardware) }, enabled = profiles.size < 64) { Text(stringResource(R.string.configure_lens)) }
                    } }
                }
            }
            HorizontalDivider()
            Button(onClick = { editing = draft() }, enabled = profiles.size < 64) { Text(stringResource(R.string.add_lens)) }
            profiles.forEach { profile ->
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(profile.name, style = MaterialTheme.typography.titleSmall)
                    Text("${profile.device} · ${profile.equivalentMin}–${profile.equivalentMax} MM", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { editing = profile }) { Text(stringResource(R.string.edit_lens)) }
                        TextButton(onClick = { profiles = profiles.filterNot { it.id == profile.id } }) { Text(stringResource(R.string.delete_lens)) }
                    }
                } }
            }
        }
    }
    editing?.let { lens -> LensProfileDialog(lens, onDismiss = { editing = null }, onSave = { changed ->
        profiles = profiles.filterNot { it.id == changed.id } + changed; editing = null
    }) }
}

@Composable
private fun LensProfileDialog(lens: LensProfile, onDismiss: () -> Unit, onSave: (LensProfile) -> Unit) {
    fun number(value: Double?) = value?.takeIf { it > 0 }?.toString().orEmpty()
    var device by rememberSaveable(lens.id) { mutableStateOf(lens.device) }
    var name by rememberSaveable(lens.id) { mutableStateOf(lens.name) }
    var min by rememberSaveable(lens.id) { mutableStateOf(number(lens.equivalentMin)) }
    var max by rememberSaveable(lens.id) { mutableStateOf(number(lens.equivalentMax)) }
    var zoomMin by rememberSaveable(lens.id) { mutableStateOf(number(lens.zoomMin)) }
    var zoomMax by rememberSaveable(lens.id) { mutableStateOf(number(lens.zoomMax)) }
    var physicalMin by rememberSaveable(lens.id) { mutableStateOf(number(lens.physicalMin)) }
    var physicalMax by rememberSaveable(lens.id) { mutableStateOf(number(lens.physicalMax)) }
    val draft = lens.copy(device=device.trim(),name=name.trim(),equivalentMin=min.toDoubleOrNull() ?: 0.0,equivalentMax=max.toDoubleOrNull() ?: 0.0,
        zoomMin=zoomMin.toDoubleOrNull(),zoomMax=zoomMax.toDoubleOrNull(),physicalMin=physicalMin.toDoubleOrNull(),physicalMax=physicalMax.toDoubleOrNull())
    val numeric = listOf(zoomMin,zoomMax,physicalMin,physicalMax).all { it.isBlank() || it.toDoubleOrNull()?.isFinite() == true }
    @Composable fun field(value: String, label: Int, update: (String)->Unit) {
        OutlinedTextField(value, { if(it.length<=256) update(it) }, Modifier.fillMaxWidth(), label={ Text(stringResource(label)) }, singleLine=true)
    }
    AlertDialog(onDismissRequest=onDismiss,title={ Text(stringResource(R.string.configure_lens)) },text={
        Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.profile_match_hint), style=MaterialTheme.typography.bodySmall)
            field(device,R.string.profile_device) { device=it }; field(name,R.string.profile_name) { name=it }
            field(min,R.string.equivalent_min) { min=it }; field(max,R.string.equivalent_max) { max=it }
            field(zoomMin,R.string.zoom_min) { zoomMin=it }; field(zoomMax,R.string.zoom_max) { zoomMax=it }
            field(physicalMin,R.string.physical_min) { physicalMin=it }; field(physicalMax,R.string.physical_max) { physicalMax=it }
        }
    },confirmButton={ TextButton(onClick={ onSave(draft) },enabled=draft.valid()&&numeric) { Text(stringResource(R.string.save)) } },
        dismissButton={ TextButton(onClick=onDismiss) { Text(stringResource(R.string.cancel)) } })
}
