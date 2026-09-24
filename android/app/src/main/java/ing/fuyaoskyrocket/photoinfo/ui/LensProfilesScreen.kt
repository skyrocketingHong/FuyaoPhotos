package ing.fuyaoskyrocket.photoinfo.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import ing.fuyaoskyrocket.photoinfo.ui.components.rememberConfirmedBack
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
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
    return if (fields.firstOrNull() != LensProfileFields.VERSION && lens.cameraId.isNotBlank()) lens.copy(hardwareDevice=LocalCameraDevice.hardwareKey) else lens
}
internal val ProfilesSaver = Saver<List<LensProfile>, ArrayList<String>>(
    save = { ArrayList(it.flatMap { lens -> lens.fields() }) },
    restore = { values ->
        val size = if (values.firstOrNull() == LensProfileFields.VERSION) LensProfileFields.SIZE else 10
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
    val context=LocalContext.current;val scope=rememberCoroutineScope();val snackbar=remember { SnackbarHostState() }
    val removedText=stringResource(R.string.lens_removed);val undoText=stringResource(R.string.undo)
    fun scan() { scope.launch {
        scanning=true;failed=false;denied=false
        inventory=withContext(Dispatchers.IO) { runCatching { CameraInventoryReader(context).scan() }.getOrNull() }
        failed=inventory==null;scanning=false
    } }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted->if(granted)scan() else denied=true }
    val hardwareDevice = LocalCameraDevice.hardwareKey
    var productName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { productName = LocalCameraDevice.productName() }
    fun draft(hardware: HardwareLens? = null) = LensProfile(UUID.randomUUID().toString(), requireNotNull(productName), "",
        hardware?.id.orEmpty(), 0.0, 0.0, physicalMin=hardware?.physicalFocals?.minOrNull(), physicalMax=hardware?.physicalFocals?.maxOrNull(),
        exifModel=exifModelHint, hardwareDevice=if(hardware==null) "" else hardwareDevice, facing=hardware?.facing.orEmpty())
    val duplicateBindings = LensBindings.hasDuplicates(profiles)
    LaunchedEffect(editedFields) {
        editedFields?.let { seed ->
            val changed=lensFromFields(seed)
            profiles=if(profiles.any { it.id==changed.id })profiles.map { if(it.id==changed.id)changed else it } else profiles+changed
            onEditConsumed()
        }
    }
    val requestBack = rememberConfirmedBack(onBack, hasChanges = profiles != initial)
    FuyaoScaffold(stringResource(R.string.lens_profiles),onBack=requestBack,snackbarHost={ SnackbarHost(snackbar, Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))) },actions={
        FuyaoAppBarAction(R.drawable.ic_plus,stringResource(R.string.add_lens),{ onEdit(draft()) },enabled=profiles.size<64&&productName!=null)
        TextButton(onClick={ onSave(profiles) },enabled=!duplicateBindings) { Text(stringResource(R.string.save_profiles)) }
    }) { padding ->
        Box(Modifier.fillMaxSize().consumeWindowInsets(padding),contentAlignment=Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max=FuyaoLayout.readable).fillMaxSize(),contentPadding=PaddingValues(
                start=padding.calculateStartPadding(LocalLayoutDirection.current)+16.dp,
                end=padding.calculateEndPadding(LocalLayoutDirection.current)+16.dp,
                top=padding.calculateTopPadding()+16.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
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
                                    profiles.filter { it.cameraId.isBlank() }.forEach { profile ->
                                        DropdownMenuItem(text={ Text(stringResource(R.string.link_existing_lens,profile.name)) },onClick={
                                            menu=false
                                            val linked=profile.copy(cameraId=hardware.id,hardwareDevice=hardwareDevice,facing=hardware.facing)
                                            onEdit(linked)
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                item { HorizontalDivider();Spacer(Modifier.height(12.dp));SectionHeading(stringResource(R.string.saved_profiles)) }
                if(duplicateBindings)item { Text(stringResource(R.string.duplicate_lens_binding),color=MaterialTheme.colorScheme.error) }
                if(profiles.isEmpty())item {
                    Text(stringResource(R.string.no_lens_profiles),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick={ onEdit(draft()) },enabled=productName!=null) { Text(stringResource(R.string.add_lens)) }
                }
                items(profiles,key={ it.id }) { profile ->
                    Row(Modifier.fillMaxWidth().animateItem().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(profile.name,style=MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.profile_summary,profile.device,profile.exifModel,profile.equivalentMin.toString(),profile.equivalentMax.toString()),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(profile.cameraId.isNotBlank()) Text(stringResource(R.string.bound_camera_id,profile.cameraId),style=MaterialTheme.typography.bodySmall)
                            if(inventory!=null && profile.hardwareDevice==hardwareDevice && inventory?.lenses?.any { it.id==profile.cameraId }==true)
                                Text(stringResource(R.string.hardware_linked),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
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
                item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) }
            }
        }
    }
}
