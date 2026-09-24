package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.presentation.EditorNotice
import androidx.compose.ui.unit.dp

class ExportNoticeVisuals(val notice: EditorNotice, override val actionLabel: String?) : SnackbarVisuals {
    override val message get()=notice.text
    override val duration=SnackbarDuration.Short
    override val withDismissAction=true
}

@Composable
fun ExportNotice(data: SnackbarData, onOpen: (EditorNotice)->Unit, onShare: (EditorNotice)->Unit) {
    val notice=(data.visuals as? ExportNoticeVisuals)?.notice
    if(notice==null || notice.photos.isEmpty()) { Snackbar(data);return }
    Snackbar(actionOnNewLine=true,action={
        Row {
            TextButton(onClick={ data.dismiss();onOpen(notice) }) { Text(stringResource(R.string.open_photo),color=MaterialTheme.colorScheme.inversePrimary) }
            TextButton(onClick={ data.dismiss();onShare(notice) }) { Text(stringResource(R.string.share),color=MaterialTheme.colorScheme.inversePrimary) }
        }
    },dismissAction={
        IconButton(onClick=data::dismiss) { Icon(painterResource(R.drawable.ic_close),stringResource(R.string.close)) }
    }) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_check),null,Modifier.size(20.dp))
            Text(notice.text)
        }
    }
}
