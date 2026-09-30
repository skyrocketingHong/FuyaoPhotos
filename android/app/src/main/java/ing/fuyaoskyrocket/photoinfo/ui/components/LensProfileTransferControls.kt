package ing.fuyaoskyrocket.photoinfo.ui.components

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.lens.LensProfile
import ing.fuyaoskyrocket.photoinfo.domain.lens.LensProfileFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LensProfileTransferControls(profiles: List<LensProfile>, onChange: (List<LensProfile>) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val clipboardLabel = stringResource(R.string.lens_profiles)
    val current by rememberUpdatedState(profiles)
    var busy by remember { mutableStateOf(false) }
    var outgoing by remember { mutableStateOf<LensProfileFile?>(null) }
    var incoming by remember { mutableStateOf<LensProfileFile?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }
    var copiedDevice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(profiles) { copiedDevice = null }
    fun apply(file: LensProfileFile) {
        try { onChange(file.merging(current)); message = R.string.lens_file_imported }
        catch (_: Exception) { message = R.string.lens_file_invalid }
    }
    fun receive(file: LensProfileFile) {
        if (current.any { it.acceptsExif(file.exifModel) }) incoming = file else apply(file)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val file = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = ByteArray(LensProfileFile.MAX_BYTES + 1); var length = 0
                        while (length < bytes.size) {
                            val count = input.read(bytes, length, bytes.size - length)
                            if (count < 0) break
                            require(count > 0); length += count
                        }
                        LensProfileFile.decode(bytes.copyOf(length))
                    } ?: error("Missing input")
                }
                receive(file)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = R.string.lens_file_invalid }
            finally { busy = false }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val file = outgoing; outgoing = null
        if (uri != null && file != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(file.encoded()) } ?: error("Missing output")
                }
                message = R.string.lens_file_exported
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = R.string.lens_file_export_failed }
            finally { busy = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try {
                        val clip = requireNotNull(clipboard.getClipEntry()?.clipData)
                        require(clip.itemCount == 1)
                        val text = requireNotNull(clip.getItemAt(0).text)
                        require(text.length in 1..LensProfileFile.MAX_BYTES)
                        val file = withContext(Dispatchers.Default) { LensProfileFile.decode(text.toString().toByteArray(Charsets.UTF_8)) }
                        receive(file)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = R.string.lens_clipboard_invalid }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.lens_clipboard_import)) }
            OutlinedButton(enabled = !busy, onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                Text(stringResource(R.string.lens_file_import))
            }
            LensDeviceExportMenu(R.string.lens_clipboard_copy, profiles, !busy) { group ->
                scope.launch {
                    busy = true
                    try {
                        val file = LensProfileFile.from(group)
                        val text = file.encoded().toString(Charsets.UTF_8)
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipboardLabel, text)))
                        copiedDevice = file.device
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = R.string.lens_clipboard_copy_failed }
                    finally { busy = false }
                }
            }
            LensDeviceExportMenu(R.string.lens_file_export, profiles, !busy) { group ->
                try { outgoing = LensProfileFile.from(group); exporter.launch(requireNotNull(outgoing).filename()) }
                catch (_: Exception) { message = R.string.lens_file_invalid }
            }
        }
        copiedDevice?.let { Text(stringResource(R.string.lens_clipboard_copied, it),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(stringResource(R.string.lens_file_description), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    incoming?.let { file ->
        AlertDialog(onDismissRequest = { incoming = null }, title = { Text(stringResource(R.string.lens_file_replace_title)) },
            text = { Text(stringResource(R.string.lens_file_replace_message, file.device)) },
            confirmButton = { TextButton(onClick = { incoming = null; apply(file) }) { Text(stringResource(R.string.lens_file_replace)) } },
            dismissButton = { TextButton(onClick = { incoming = null }) { Text(stringResource(R.string.cancel)) } })
    }
    message?.let { value ->
        AlertDialog(onDismissRequest = { message = null }, title = { Text(stringResource(R.string.lens_profiles)) },
            text = { Text(stringResource(value)) }, confirmButton = {
                TextButton(onClick = { message = null }) { Text(stringResource(R.string.close)) }
            })
    }
}

@Composable
private fun LensDeviceExportMenu(label: Int, profiles: List<LensProfile>, enabled: Boolean,
                                 onSelect: (List<LensProfile>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(enabled = enabled && profiles.isNotEmpty(), onClick = { expanded = true }) {
            Text(stringResource(label))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            profiles.groupBy { LensProfile.normalize(it.exifModel) }.values.forEach { group ->
                DropdownMenuItem(text = { Text(group.first().device) }, onClick = { expanded = false; onSelect(group) })
            }
        }
    }
}
