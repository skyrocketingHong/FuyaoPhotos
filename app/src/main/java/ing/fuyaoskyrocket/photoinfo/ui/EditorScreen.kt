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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

private enum class PhotoPage { EDITOR, SETTINGS, LENSES, LENS_EDIT, PREVIEW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel = viewModel()) {
    val state = vm.state
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var original by rememberSaveable { mutableStateOf(false) }
    var showPhotoMenu by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val navigation = rememberNavController()
    var settingsLenses by rememberSaveable(stateSaver = ProfilesSaver) { mutableStateOf(state.settings.lenses) }
    var editingLens by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var editedLens by rememberSaveable { mutableStateOf<List<String>?>(null) }
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
    NavHost(
        navController = navigation,
        startDestination = PhotoPage.EDITOR.name,
        enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 1.04f) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(220)) + scaleOut(tween(220), targetScale = .92f) },
    ) {
        composable(PhotoPage.EDITOR.name) {
            FuyaoScaffold(title=stringResource(R.string.editor_title),actions={
                Box {
                    FuyaoIconButton(R.drawable.ic_photo_add,stringResource(R.string.select_photo),{ showPhotoMenu=true },enabled=!state.busy)
                    DropdownMenu(showPhotoMenu,onDismissRequest={ showPhotoMenu=false }) {
                        DropdownMenuItem(text={ Text(stringResource(R.string.from_gallery)) },onClick={
                            showPhotoMenu=false;photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        DropdownMenuItem(text={ Text(stringResource(R.string.from_file)) },onClick={ showPhotoMenu=false;filePicker.launch(arrayOf("image/*")) })
                    }
                }
                FuyaoIconButton(R.drawable.ic_export,stringResource(R.string.export),{ showExport=true },enabled=state.canExport)
                Box {
                    FuyaoIconButton(R.drawable.ic_more,stringResource(R.string.more),{ showMore=true })
                    DropdownMenu(showMore,onDismissRequest={ showMore=false }) {
                        DropdownMenuItem(text={ Text(stringResource(R.string.settings)) },enabled=!state.busy,onClick={ showMore=false;settingsLenses=state.settings.lenses;navigation.navigate(PhotoPage.SETTINGS.name) })
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
                                val preview:@Composable (Modifier)->Unit={ m -> EditorPreviewPane(state,original,{ original=!original },{ navigation.navigate(PhotoPage.PREVIEW.name) },m,bottomSafe=wide) }
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
        composable(PhotoPage.SETTINGS.name) {
            SettingsScreen(state.settings.copy(lenses = settingsLenses), state.original != null,
                onManageLenses = { editedLens = null; navigation.navigate(PhotoPage.LENSES.name) },
                onBack = { navigation.popBackStack() },
                onSave = { settings, apply ->
                    vm.saveSettings(settings)
                    if (apply) vm.applyDefaultAuthor()
                    navigation.popBackStack(PhotoPage.EDITOR.name, false)
                })
        }
        composable(PhotoPage.LENSES.name) {
            LensProfilesScreen(settingsLenses, state.sourceDevice, editedLens,
                onEditConsumed = { editedLens = null },
                onEdit = { editingLens = it.fields(); navigation.navigate(PhotoPage.LENS_EDIT.name) },
                onBack = { navigation.popBackStack() },
                onSave = { profiles ->
                    vm.saveSettings(state.settings.copy(lenses = profiles))
                    settingsLenses = profiles
                    navigation.popBackStack()
                })
        }
        composable(PhotoPage.LENS_EDIT.name) {
            editingLens?.let { fields ->
                LensEditScreen(lensFromFields(fields), onBack = { navigation.popBackStack() },
                    onSave = { editedLens = it.fields(); navigation.popBackStack() })
            }
        }
        composable(PhotoPage.PREVIEW.name) {
            val bitmap = if (original) state.original else state.preview
            if (bitmap != null) FullScreenPreview(bitmap) { navigation.popBackStack() }
            else LaunchedEffect(state.busy) { if (!state.busy) navigation.popBackStack() }
        }
    }
    if(showAbout)AboutDialog { showAbout=false }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDialog(width: Int, height: Int, jpegRequired: Boolean, keepMetadata: Boolean,
    onMetadata: (Boolean) -> Unit, onDismiss: () -> Unit, onExport: (ExportFormat) -> Unit) {
    var png by rememberSaveable(jpegRequired) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.export), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.export_size, width, height),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !png, onClick = { png = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2), modifier = Modifier.weight(1f)) {
                    Text("JPEG · 97%")
                }
                SegmentedButton(selected = png, onClick = { png = true }, enabled = !jpegRequired,
                    shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.weight(1f)) {
                    Text("PNG")
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .toggleable(value = keepMetadata, role = Role.Checkbox, onValueChange = onMetadata),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = keepMetadata, onCheckedChange = null)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.keep_metadata), style = MaterialTheme.typography.bodyLarge)
            }
            Text(stringResource(R.string.export_boundary), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = !submitting, onClick = {
                submitting = true
                scope.launch {
                    try {
                        sheetState.hide()
                        onExport(if (png && !jpegRequired) ExportFormat.PNG else ExportFormat.JPEG)
                    } finally { submitting = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
            TextButton(enabled = !submitting, onClick = {
                scope.launch { sheetState.hide(); onDismiss() }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}
