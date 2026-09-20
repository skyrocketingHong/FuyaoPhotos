package ing.fuyaoskyrocket.photoinfo.ui

import android.Manifest
import android.net.Uri
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import android.content.ClipData
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.text.style.TextOverflow
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.*
import ing.fuyaoskyrocket.photoinfo.ui.components.EditorPreviewPane
import ing.fuyaoskyrocket.photoinfo.ui.components.AboutDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.data.export.PhotoExporter
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportFormat
import ing.fuyaoskyrocket.photoinfo.presentation.EditorViewModel
import ing.fuyaoskyrocket.photoinfo.ui.components.EditorControls
import ing.fuyaoskyrocket.photoinfo.ui.components.FullScreenPreview
import ing.fuyaoskyrocket.photoinfo.ui.components.PhotoPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel = viewModel()) {
    val state = vm.state
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    var original by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showPhotoMenu by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val pageState=rememberSaveableStateHolder()
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val photoPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingPhoto?.let { value -> pendingPhoto = null; vm.importPhoto(Uri.parse(value)) }
    }
    val importSelectedPhoto: (Uri) -> Unit = { uri ->
        if (state.settings.resolvePhotoLocation && Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingPhoto = uri.toString()
            photoPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        } else vm.importPhoto(uri)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(importSelectedPhoto) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(importSelectedPhoto) }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importFont) }
    val jpegDestination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) {
        if (it != null) vm.export(ExportFormat.JPEG, it)
    }
    val pngDestination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) {
        if (it != null) vm.export(ExportFormat.PNG, it)
    }
    val shareLabel = stringResource(R.string.share)
    LaunchedEffect(state.notice) {
        state.notice?.let { notice ->
            val exported = state.exported
            val result = snackbar.showSnackbar(notice, actionLabel = if (exported != null) shareLabel else null)
            vm.clearNotice()
            if (result == SnackbarResult.ActionPerformed && exported != null) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = exported.format.mime
                    putExtra(Intent.EXTRA_STREAM, exported.uri)
                    clipData = ClipData.newUri(context.contentResolver, "Fuyao Photo Info", exported.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, shareLabel))
            }
        }
    }
    if (showSettings) {
        pageState.SaveableStateProvider("settings") {
            SettingsScreen(state.settings, state.original != null, photoDevice = state.sourceDevice, onBack = { pageState.removeState("settings"); showSettings = false }, onSave = { settings, apply ->
                vm.saveSettings(settings)
                if (apply) vm.applyDefaultAuthor()
                pageState.removeState("settings")
                showSettings = false
            })
        }
        return
    }
    pageState.SaveableStateProvider("editor") {
        FuyaoScaffold(title=stringResource(R.string.app_name),brand=true,actions={
            Box {
                FuyaoIconButton(R.drawable.ic_photo_add,stringResource(R.string.select_photo),{ showPhotoMenu=true },enabled=!state.busy)
                DropdownMenu(showPhotoMenu,onDismissRequest={ showPhotoMenu=false }) {
                    DropdownMenuItem(text={ Text(stringResource(R.string.from_gallery)) },onClick={
                        showPhotoMenu=false;photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    DropdownMenuItem(text={ Text(stringResource(R.string.from_file)) },onClick={ showPhotoMenu=false;filePicker.launch(arrayOf("image/*")) })
                }
            }
            FuyaoIconButton(R.drawable.ic_export,stringResource(R.string.export),{ showExport=true },enabled=state.canExport,prominent=true)
            Box {
                FuyaoIconButton(R.drawable.ic_more,stringResource(R.string.more),{ showMore=true })
                DropdownMenu(showMore,onDismissRequest={ showMore=false }) {
                    DropdownMenuItem(text={ Text(stringResource(R.string.settings)) },enabled=!state.busy,onClick={ showMore=false;showSettings=true })
                    DropdownMenuItem(text={ Text(stringResource(R.string.about)) },onClick={ showMore=false;showAbout=true })
                }
            }
        },snackbarHost={ SnackbarHost(snackbar) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                if(state.original==null) {
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                        Column(Modifier.widthIn(max=420.dp).verticalScroll(rememberScrollState()).padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            Icon(painterResource(R.drawable.ic_photo_info),null,Modifier.size(56.dp),MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.empty_title),style=MaterialTheme.typography.headlineSmall)
                            Text(stringResource(R.string.empty_hint),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick={ photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },enabled=!state.busy) { Text(stringResource(R.string.from_gallery)) }
                            TextButton(onClick={ filePicker.launch(arrayOf("image/*")) },enabled=!state.busy) { Text(stringResource(R.string.from_file)) }
                            if(state.busy)CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)
                        }
                    }
                } else {
                    state.mediaMessage?.let {
                        Text(stringResource(it),Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall,
                            color=if(state.preservationBlocked)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.previewError?.let { Text(it,Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error) }
                    Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.TopCenter) {
                        BoxWithConstraints(Modifier.widthIn(max=FuyaoLayout.editor).fillMaxSize()) {
                            val wide=maxWidth>=600.dp
                            val inspectorWidth=if(maxWidth<840.dp)320.dp else FuyaoLayout.inspector
                            val previewHeight=if(maxHeight>=maxWidth+240.dp)maxWidth else maxHeight*.45f
                            val preview:@Composable (Modifier)->Unit={ m -> EditorPreviewPane(state,original,{ original=!original },{ fullScreen=true },m,bottomSafe=wide) }
                            val controls:@Composable (Modifier)->Unit={ m -> EditorControls(state,vm::updateField,vm::updateStyle,vm::resetFields,
                                { fontPicker.launch(arrayOf("*/*")) },vm::resetFont,vm::resolveLocation,m) }
                            if(wide)Row(Modifier.fillMaxSize()) {
                                preview(Modifier.weight(1f).fillMaxHeight())
                                VerticalDivider()
                                controls(Modifier.width(inspectorWidth).fillMaxHeight())
                            } else Column(Modifier.fillMaxSize()) {
                                preview(Modifier.fillMaxWidth().height(previewHeight))
                                controls(Modifier.fillMaxWidth().weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
    if(showAbout)AboutDialog { showAbout=false }
    if (fullScreen) (if (original) state.original else state.preview)?.let { bitmap ->
        FullScreenPreview(bitmap) { fullScreen = false }
    }
    if (showExport) ExportDialog(
        width = state.width, height = state.height, jpegRequired = state.jpegRequired, keepMetadata = state.keepCaptureMetadata,
        onMetadata = vm::setKeepMetadata, onDismiss = { showExport = false }, onExport = { format ->
            showExport = false
            if (Build.VERSION.SDK_INT >= 29) vm.export(format)
            else if (format == ExportFormat.JPEG) jpegDestination.launch(PhotoExporter.filename(format, state.motionPhoto))
            else pngDestination.launch(PhotoExporter.filename(format, state.motionPhoto))
        },
    )
    state.error?.let { message ->
        AlertDialog(onDismissRequest = vm::clearError, title = { Text(stringResource(R.string.error_title)) },
            text = { Text(message, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text(stringResource(R.string.close)) } })
    }
}

@Composable
private fun ExportDialog(width: Int, height: Int, jpegRequired: Boolean, keepMetadata: Boolean, onMetadata: (Boolean) -> Unit,
    onDismiss: () -> Unit, onExport: (ExportFormat) -> Unit) {
    var png by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.export)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.export_size, width, height))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(!png, onClick = { png = false }, label = { Text("JPEG · 97%") })
                    FilterChip(png, onClick = { png = true }, enabled = !jpegRequired, label = { Text("PNG") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = keepMetadata, onCheckedChange = onMetadata)
                    Text(stringResource(R.string.keep_metadata), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.export_boundary), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onExport(if (png && !jpegRequired) ExportFormat.PNG else ExportFormat.JPEG) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
