package ing.fuyaoskyrocket.photoinfo.features.colors

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import ing.fuyaoskyrocket.photoinfo.R
import java.io.File
import java.util.UUID

@Composable
fun rememberColorCamera(onCaptured: (List<Uri>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onCaptured)
    var destination by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = destination?.let(Uri::parse)
        destination = null
        if (success && uri != null) callback(listOf(uri))
    }
    val launch: () -> Unit = {
        runCatching {
            val folder = File(context.cacheDir, "camera").apply { mkdirs() }
            folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
            val file = File(folder, "${UUID.randomUUID()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            destination = uri.toString()
            capture.launch(uri)
        }.onFailure { failed = true }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launch() else failed = true
    }
    if (failed) AlertDialog(onDismissRequest = { failed = false },
        text = { Text(stringResource(R.string.colors_camera_failed)) },
        confirmButton = { TextButton({ failed = false }) { Text(stringResource(R.string.close)) } })
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launch()
        else permission.launch(Manifest.permission.CAMERA)
    }
}
