package ing.fuyaoskyrocket.photoinfo.ui

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
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::importPhoto) }
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
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium) },
                actions = {
                    TextButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, enabled = !state.busy) { Text(stringResource(R.string.select_photo)) }
                    TextButton(onClick = { showExport = true }, enabled = state.canExport) { Text(stringResource(R.string.export)) }
                })
        }, snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (state.busy || state.rendering) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.exporting) Text(stringResource(R.string.exporting),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
            state.previewError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth >= 840.dp || (maxWidth >= 600.dp && maxWidth > maxHeight)
                val preview: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier) {
                        PhotoPreview(if (original) state.original else state.preview, Modifier.weight(1f).fillMaxWidth())
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (state.width > 0) "${state.width} × ${state.height}" else stringResource(R.string.local_only),
                                style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(selected = original, onClick = { original = !original },
                                    enabled = state.original != null, label = { Text(stringResource(R.string.original)) })
                                TextButton(onClick = { fullScreen = true }, enabled = state.preview != null) {
                                    Text(stringResource(R.string.enlarge))
                                }
                            }
                        }
                    }
                }
                val controls: @Composable (Modifier) -> Unit = { modifier ->
                    EditorControls(state, vm::updateField, vm::updateStyle, vm::resetFields,
                        onImportFont = { fontPicker.launch(arrayOf("*/*")) }, onResetFont = vm::resetFont,
                        modifier = modifier)
                }
                if (wide) Row(Modifier.fillMaxSize()) {
                    preview(Modifier.weight(1f).fillMaxHeight())
                    controls(Modifier.width(360.dp).fillMaxHeight())
                } else Column(Modifier.fillMaxSize()) {
                    preview(Modifier.weight(1f).fillMaxWidth())
                    controls(Modifier.weight(1.1f).fillMaxWidth())
                }
            }
        }
    }
    if (fullScreen) (if (original) state.original else state.preview)?.let { bitmap ->
        FullScreenPreview(bitmap) { fullScreen = false }
    }
    if (showExport) ExportDialog(
        width = state.width, height = state.height, keepMetadata = state.keepCaptureMetadata,
        onMetadata = vm::setKeepMetadata, onDismiss = { showExport = false }, onExport = { format ->
            showExport = false
            if (Build.VERSION.SDK_INT >= 29) vm.export(format)
            else if (format == ExportFormat.JPEG) jpegDestination.launch(PhotoExporter.filename(format))
            else pngDestination.launch(PhotoExporter.filename(format))
        },
    )
    state.error?.let { message ->
        AlertDialog(onDismissRequest = vm::clearError, title = { Text(stringResource(R.string.error_title)) },
            text = { Text(message, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text(stringResource(R.string.close)) } })
    }
}

@Composable
private fun ExportDialog(width: Int, height: Int, keepMetadata: Boolean, onMetadata: (Boolean) -> Unit,
    onDismiss: () -> Unit, onExport: (ExportFormat) -> Unit) {
    var png by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.export)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.export_size, width, height))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(!png, onClick = { png = false }, label = { Text("JPEG · 97%") })
                    FilterChip(png, onClick = { png = true }, label = { Text("PNG") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = keepMetadata, onCheckedChange = onMetadata)
                    Text(stringResource(R.string.keep_metadata), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.export_boundary), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onExport(if (png) ExportFormat.PNG else ExportFormat.JPEG) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
