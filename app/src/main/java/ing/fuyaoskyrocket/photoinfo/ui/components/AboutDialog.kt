package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.BuildConfig
import ing.fuyaoskyrocket.photoinfo.R

@Composable
fun AboutDialog(onDismiss:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={ Text(stringResource(R.string.app_name)) },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("${BuildConfig.MARKETING_VERSION} (${BuildConfig.BUILD_NUMBER}) · ${BuildConfig.BUILD_TYPE}",style=MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.about_summary),style=MaterialTheme.typography.bodyMedium)
            Text("AGPL-3.0-only · 扶摇skyrocketing",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    },confirmButton={ TextButton(onClick=onDismiss) { Text(stringResource(R.string.close)) } })
}
