package ing.fuyaoskyrocket.photoinfo.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import ing.fuyaoskyrocket.photoinfo.ui.components.rememberConfirmedBack
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.data.camera.*
import ing.fuyaoskyrocket.photoinfo.domain.lens.LensProfile
import ing.fuyaoskyrocket.photoinfo.domain.lens.LensProfileFields
import ing.fuyaoskyrocket.photoinfo.domain.lens.LensBindings
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun LensProfile.fields() = LensProfileFields.encode(this)
internal fun lensFromFields(fields: List<String>): LensProfile {
    val lens = LensProfileFields.decode(fields)
    return if (LensProfileFields.sizeFor(fields.firstOrNull()) == 10 && lens.cameraId.isNotBlank()) lens.copy(hardwareDevice=LocalCameraDevice.hardwareKey) else lens
}
internal val ProfilesSaver = Saver<List<LensProfile>, ArrayList<String>>(
    save = { ArrayList(it.flatMap { lens -> lens.fields() }) },
    restore = { values ->
        val size = LensProfileFields.sizeFor(values.firstOrNull())
        require(values.size % size == 0)
        values.chunked(size).map(::lensFromFields)
    })

@Composable
fun LensProfilesScreen(initial:List<LensProfile>,exifModelHint:String="",editedFields:List<String>?,onEditConsumed:()->Unit,
    onEdit:(LensProfile)->Unit,onBack:()->Unit,onSave:(List<LensProfile>)->Unit) {
    var profiles by rememberSaveable(stateSaver=ProfilesSaver) { mutableStateOf(initial) }
    var inventory by remember { mutableStateOf<CameraInventory?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var bindingProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var bindingFailed by remember { mutableStateOf(false) }
    var expandedDevices by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val context=LocalContext.current;val scope=rememberCoroutineScope();val snackbar=remember { SnackbarHostState() }
    val removedText=stringResource(R.string.lens_removed);val undoText=stringResource(R.string.undo)
    val hardwareDevice = LocalCameraDevice.hardwareKey
    var productName by remember { mutableStateOf<String?>(null) }
    val aliases = LocalCameraDevice.aliases(productName)
    fun reconcile(values: List<LensProfile>) = inventory?.let {
        LensBindings.reconcile(values, it.lenses, hardwareDevice, LocalCameraDevice.aliases(productName))
    } ?: values
    fun scan() { scope.launch {
        scanning=true;failed=false;denied=false
        inventory=withContext(Dispatchers.IO) { runCatching { CameraInventoryReader(context).scan() }.getOrNull() }
        profiles = reconcile(profiles)
        failed=inventory==null;scanning=false
    } }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted->if(granted)scan() else denied=true }
    LaunchedEffect(Unit) {
        productName = LocalCameraDevice.productName()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scan()
    }
    LaunchedEffect(productName) { profiles = reconcile(profiles) }
    fun draft(hardware: HardwareLens? = null) = LensProfile(UUID.randomUUID().toString(), requireNotNull(productName), "",
        hardware?.id.orEmpty(), 0.0, 0.0, physicalMin=hardware?.physicalFocals?.minOrNull(), physicalMax=hardware?.physicalFocals?.maxOrNull(),
        exifModel=exifModelHint, hardwareDevice=if(hardware==null) "" else hardwareDevice,
        hardwareModel=if(hardware==null) "" else hardwareDevice, facing=hardware?.facing.orEmpty())
    val duplicateBindings = LensBindings.hasDuplicates(profiles)
    LaunchedEffect(editedFields) {
        editedFields?.let { seed ->
            val changed=lensFromFields(seed)
            profiles=reconcile(if(profiles.any { it.id==changed.id })profiles.map { if(it.id==changed.id)changed else it } else profiles+changed)
            onEditConsumed()
        }
    }
    val inventoryItems: LazyListScope.() -> Unit = {
                item { ing.fuyaoskyrocket.photoinfo.ui.components.LensProfileTransferControls(profiles) {
                    profiles = reconcile(it)
                    if (inventory == null) permission.launch(Manifest.permission.CAMERA)
                } }
                item {
                    SectionHeading(stringResource(R.string.scan_lenses),stringResource(R.string.inventory_hint))
                    OutlinedButton(onClick={ permission.launch(Manifest.permission.CAMERA) },enabled=!scanning) { Text(stringResource(R.string.scan_lenses)) }
                    if(scanning)LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(denied) {
                        Text(stringResource(R.string.camera_permission_denied),style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))) }) {
                            Text(stringResource(R.string.open_app_settings))
                        }
                    }
                    if(failed)Text(stringResource(R.string.inventory_failed),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                }
                inventory?.let { result ->
                    val unconfigured = LensBindings.unconfigured(result.lenses.map { it.id }, profiles, hardwareDevice).toSet()
                    item {
                        Text(stringResource(R.string.inventory_count,result.lenses.size,result.logicalCount),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.inventory_configured,result.lenses.size-unconfigured.size,unconfigured.size),style=MaterialTheme.typography.bodySmall)
                        if(result.incomplete)Text(stringResource(R.string.inventory_incomplete),style=MaterialTheme.typography.bodySmall)
                        if(unconfigured.isEmpty())Text(stringResource(R.string.all_lenses_configured),style=MaterialTheme.typography.bodyMedium)
                    }
                    items(result.lenses.filter { it.id in unconfigured },key={ "hardware-${it.id}" }) { hardware ->
                        var menu by remember(hardware.id) { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.hardware_identity,stringResource(when(hardware.facing) { "FRONT"->R.string.lens_front;"BACK"->R.string.lens_back;else->R.string.lens_external }),hardware.id),style=MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.hardware_values,hardware.physicalFocals.joinToString(),hardware.apertures.joinToString()),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box {
                                FuyaoIconButton(R.drawable.ic_plus,stringResource(R.string.configure_lens),{ menu=true },enabled=productName!=null)
                                DropdownMenu(menu,onDismissRequest={ menu=false }) {
                                    DropdownMenuItem(text={ Text(stringResource(R.string.add_lens)) },enabled=profiles.size<64,
                                        onClick={ menu=false;onEdit(draft(hardware)) })
                                    profiles.filter { it.hardwareDevice.isBlank() && LensBindings.isCurrent(it, hardwareDevice, aliases) }.forEach { profile ->
                                        DropdownMenuItem(text={ Text(stringResource(R.string.link_existing_lens,profile.name)) },
                                            enabled = LensBindings.compatible(profile, hardware, false), onClick={
                                            menu=false
                                            try { profiles = LensBindings.bind(profiles, profile.id, hardware.id, result.lenses, hardwareDevice) }
                                            catch (_: IllegalArgumentException) { bindingFailed = true }
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
    }
    val savedItems: LazyListScope.() -> Unit = {
                item { HorizontalDivider();Spacer(Modifier.height(12.dp));SectionHeading(stringResource(R.string.saved_profiles)) }
                if(duplicateBindings)item { Text(stringResource(R.string.duplicate_lens_binding),color=MaterialTheme.colorScheme.error) }
                if(profiles.isEmpty())item {
                    Text(stringResource(R.string.no_lens_profiles),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick={ onEdit(draft()) },enabled=productName!=null) { Text(stringResource(R.string.add_lens)) }
                }
                LensBindings.groups(profiles, hardwareDevice, aliases).forEach { group ->
                    val model = group.model
                    val lenses = group.profiles
                    val expanded = model in expandedDevices
                    item(key = "device-$model") {
                        val expandedLabel = stringResource(if (expanded) R.string.lens_group_expanded else R.string.lens_group_collapsed)
                        ListItem(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) {
                            expandedDevices = if (expanded) expandedDevices - model else expandedDevices + model
                        }.semantics { heading(); stateDescription = expandedLabel },
                            headlineContent = { Text(group.device, style = MaterialTheme.typography.titleMedium) },
                            overlineContent = if (group.isCurrent) { { Text(stringResource(R.string.lens_group_current)) } } else null,
                            supportingContent = { Text(if (lenses.size == 1) stringResource(R.string.lens_group_one) else stringResource(R.string.lens_group_count, lenses.size)) },
                            trailingContent = { Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, modifier = Modifier.rotate(if (expanded) 90f else 0f)) })
                    }
                    if (expanded) {
                        item(key = "device-details-$model") {
                            if (LensProfile.normalize(group.device) != model) Text(lenses.first().exifModel,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!group.isCurrent) TextButton(onClick = {
                                profiles = reconcile(profiles.map { if (it.acceptsExif(model)) it.copy(hardwareModel=hardwareDevice,hardwareDevice="") else it })
                                if (inventory == null) permission.launch(Manifest.permission.CAMERA)
                            }) { Text(stringResource(R.string.lens_hardware_this_device)) }
                        }
                        items(lenses,key={ "lens-${it.id}" }) { profile ->
                            Row(Modifier.fillMaxWidth().animateItem().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text(profile.name,style=MaterialTheme.typography.titleMedium)
                                    val low = ing.fuyaoskyrocket.photoinfo.domain.metadata.MetadataFormatting.number(profile.equivalentMin, 2)
                                    val high = ing.fuyaoskyrocket.photoinfo.domain.metadata.MetadataFormatting.number(profile.equivalentMax, 2)
                                    Text(if (profile.equivalentMin == profile.equivalentMax) "$low mm" else stringResource(R.string.lens_group_range,low,high),
                                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    if(profile.cameraId.isNotBlank()) Text(stringResource(if (profile.hardwareDevice == hardwareDevice) R.string.bound_camera_id else R.string.lens_hardware_id_hint,profile.cameraId),style=MaterialTheme.typography.bodySmall)
                                    if(inventory!=null && profile.hardwareDevice==hardwareDevice && inventory?.lenses?.any { it.id==profile.cameraId }==true)
                                        Text(stringResource(R.string.hardware_linked),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                                    TextButton(onClick = {
                                        bindingProfileId = profile.id
                                        if (inventory == null) permission.launch(Manifest.permission.CAMERA)
                                    }) { Text(stringResource(R.string.lens_hardware_bind)) }
                                }
                                FuyaoIconButton(R.drawable.ic_edit,stringResource(R.string.lens_action,stringResource(R.string.edit_lens),profile.name),{ onEdit(profile) })
                                FuyaoIconButton(R.drawable.ic_delete,stringResource(R.string.lens_action,stringResource(R.string.delete_lens),profile.name),{
                                    val index=profiles.indexOfFirst { it.id==profile.id }
                                    profiles=profiles.filterNot { it.id==profile.id }
                                    scope.launch {
                                        if(snackbar.showSnackbar(removedText,actionLabel=undoText,withDismissAction=true,duration=SnackbarDuration.Long)==SnackbarResult.ActionPerformed && profiles.none { it.id==profile.id }) {
                                            profiles=profiles.toMutableList().apply { add(index.coerceIn(0,size),profile) }
                                        }
                                    }
                                })
                            }
                        }
                    }
                }
    }
    val requestBack = rememberConfirmedBack(onBack, hasChanges = profiles != initial)
    profiles.firstOrNull { it.id == bindingProfileId }?.let { profile ->
        ing.fuyaoskyrocket.photoinfo.ui.components.LensHardwareBindingDialog(profile, profiles, inventory?.lenses.orEmpty(), hardwareDevice,
            onDismiss = { bindingProfileId = null }, onSelect = { cameraId ->
                try {
                    profiles = if (cameraId == null) profiles.map { if (it.id == profile.id) it.copy(cameraId="",hardwareDevice="") else it }
                    else LensBindings.bind(profiles, profile.id, cameraId, inventory?.lenses.orEmpty(), hardwareDevice)
                } catch (_: IllegalArgumentException) { bindingFailed = true }
                bindingProfileId = null
            })
    }
    if (bindingFailed) AlertDialog(onDismissRequest = { bindingFailed=false }, title = { Text(stringResource(R.string.lens_hardware_bind)) },
        text = { Text(stringResource(R.string.lens_hardware_invalid)) },
        confirmButton = { TextButton(onClick = { bindingFailed=false }) { Text(stringResource(R.string.close)) } })
    FuyaoScaffold(stringResource(R.string.lens_profiles),onBack=requestBack,
        snackbarHost={ SnackbarHost(snackbar, Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))) },
        actions={
            FuyaoAppBarAction(R.drawable.ic_plus,stringResource(R.string.add_lens),{ onEdit(draft()) },enabled=profiles.size<64&&productName!=null)
            TextButton(onClick={ onSave(profiles) },enabled=!duplicateBindings) { Text(stringResource(R.string.save_profiles)) }
        }) { padding ->
        FuyaoAdaptivePage(padding,
            single = { modifier ->
                Box(modifier,contentAlignment=Alignment.TopCenter) {
                    FuyaoPageList(Modifier.widthIn(max=FuyaoLayout.readable).fillMaxSize()) {
                        inventoryItems(); savedItems()
                    }
                }
            },
            leading = { modifier ->
                FuyaoPageList(modifier) { inventoryItems() }
            },
            trailing = { modifier ->
                FuyaoPageList(modifier) {
                    savedItems()
                }
            })
    }

}
