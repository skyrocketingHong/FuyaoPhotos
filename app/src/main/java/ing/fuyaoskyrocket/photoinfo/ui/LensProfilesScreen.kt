package ing.fuyaoskyrocket.photoinfo.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.data.camera.*
import ing.fuyaoskyrocket.photoinfo.domain.lens.LensProfile
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun LensProfile.fields()=arrayListOf(id,device,name,cameraId,equivalentMin.toString(),equivalentMax.toString(),zoomMin?.toString().orEmpty(),zoomMax?.toString().orEmpty(),physicalMin?.toString().orEmpty(),physicalMax?.toString().orEmpty())
internal fun lensFromFields(p:List<String>)=LensProfile(p[0],p[1],p[2],p[3],p[4].toDouble(),p[5].toDouble(),p[6].toDoubleOrNull(),p[7].toDoubleOrNull(),p[8].toDoubleOrNull(),p[9].toDoubleOrNull())
private val ProfilesSaver=Saver<List<LensProfile>,ArrayList<String>>(save={ ArrayList(it.flatMap { lens->lens.fields() }) },restore={ it.chunked(10).map(::lensFromFields) })

@Composable
fun LensProfilesScreen(initial:List<LensProfile>,deviceHint:String="",onBack:()->Unit,onSave:(List<LensProfile>)->Unit) {
    var profiles by rememberSaveable(stateSaver=ProfilesSaver) { mutableStateOf(initial) }
    var inventory by remember { mutableStateOf<CameraInventory?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var editorSeed by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
    val context=LocalContext.current;val scope=rememberCoroutineScope();val snackbar=remember { SnackbarHostState() }
    val removedText=stringResource(R.string.lens_removed);val undoText=stringResource(R.string.undo)
    fun scan() { scope.launch {
        scanning=true;failed=false
        inventory=withContext(Dispatchers.IO) { runCatching { CameraInventoryReader(context).scan() }.getOrNull() }
        failed=inventory==null;scanning=false
    } }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted->if(granted)scan() else failed=true }
    fun draft(hardware:HardwareLens?=null)=LensProfile(UUID.randomUUID().toString(),deviceHint.ifBlank { "${Build.MANUFACTURER} ${Build.MODEL}" },"",
        hardware?.id.orEmpty(),0.0,0.0,physicalMin=hardware?.physicalFocals?.minOrNull(),physicalMax=hardware?.physicalFocals?.maxOrNull())
    editorSeed?.let { seed ->
        LensEditScreen(lensFromFields(seed),onBack={ editorSeed=null },onSave={ changed ->
            profiles=if(profiles.any { it.id==changed.id })profiles.map { if(it.id==changed.id)changed else it } else profiles+changed
            editorSeed=null
        })
        return
    }
    BackHandler(onBack=onBack)
    FuyaoScaffold(stringResource(R.string.lens_profiles),onBack=onBack,snackbarHost={ SnackbarHost(snackbar) },actions={
        FuyaoIconButton(R.drawable.ic_plus,stringResource(R.string.add_lens),{ editorSeed=draft().fields() },enabled=profiles.size<64)
        TextButton(onClick={ onSave(profiles) }) { Text(stringResource(R.string.save)) }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),contentAlignment=Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max=FuyaoLayout.readable).fillMaxSize(),contentPadding=PaddingValues(horizontal=16.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                item {
                    SectionHeading(stringResource(R.string.scan_lenses),stringResource(R.string.inventory_hint))
                    OutlinedButton(onClick={ permission.launch(Manifest.permission.CAMERA) },enabled=!scanning) { Text(stringResource(R.string.scan_lenses)) }
                    if(scanning)LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(failed)Text(stringResource(R.string.inventory_failed),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                }
                inventory?.let { result ->
                    item {
                        Text(stringResource(R.string.inventory_count,result.lenses.size,result.logicalCount),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(result.incomplete)Text(stringResource(R.string.inventory_incomplete),style=MaterialTheme.typography.bodySmall)
                    }
                    items(result.lenses,key={ "hardware-${it.id}" }) { hardware ->
                        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text("${stringResource(when(hardware.facing) { "FRONT"->R.string.lens_front;"BACK"->R.string.lens_back;else->R.string.lens_external })} · ID ${hardware.id}",style=MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.hardware_values,hardware.physicalFocals.joinToString(),hardware.apertures.joinToString()),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FuyaoIconButton(R.drawable.ic_plus,stringResource(R.string.configure_lens),{ editorSeed=draft(hardware).fields() },enabled=profiles.size<64)
                        }
                    }
                }
                item { HorizontalDivider();Spacer(Modifier.height(12.dp));SectionHeading(stringResource(R.string.saved_profiles)) }
                if(profiles.isEmpty())item {
                    Text(stringResource(R.string.no_lens_profiles),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick={ editorSeed=draft().fields() }) { Text(stringResource(R.string.add_lens)) }
                }
                items(profiles,key={ it.id }) { profile ->
                    Row(Modifier.fillMaxWidth().animateItem().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(profile.name,style=MaterialTheme.typography.titleMedium)
                            Text("${profile.device}\n${profile.equivalentMin}–${profile.equivalentMax} MM",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FuyaoIconButton(R.drawable.ic_edit,"${stringResource(R.string.edit_lens)} ${profile.name}",{ editorSeed=profile.fields() })
                        FuyaoIconButton(R.drawable.ic_delete,"${stringResource(R.string.delete_lens)} ${profile.name}",{
                            val index=profiles.indexOfFirst { it.id==profile.id }
                            profiles=profiles.filterNot { it.id==profile.id }
                            scope.launch {
                                if(snackbar.showSnackbar(removedText,actionLabel=undoText,withDismissAction=true)==SnackbarResult.ActionPerformed && profiles.none { it.id==profile.id }) {
                                    profiles=profiles.toMutableList().apply { add(index.coerceIn(0,size),profile) }
                                }
                            }
                        })
                    }
                }
                item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
            }
        }
    }
}
