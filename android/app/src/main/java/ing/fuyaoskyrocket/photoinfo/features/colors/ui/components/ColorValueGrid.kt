package ing.fuyaoskyrocket.photoinfo.features.colors.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoFormSection
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoSpacing

internal data class ColorValueItem(val label: String, val value: String?, val fullWidth: Boolean = false)

@Composable
internal fun ColorValuePanel(items: List<ColorValueItem>, modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null) {
    FuyaoFormSection(modifier = modifier, verticalSpacing = 0.dp,
        contentPadding = PaddingValues(horizontal = FuyaoSpacing.cardInset, vertical = FuyaoSpacing.small)) {
        if (header != null) {
            header()
            Spacer(Modifier.height(FuyaoSpacing.small))
        }
        items.forEachIndexed { index, item ->
            ColorValueCell(item)
            if (index < items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
        }
    }
}

@Composable
private fun ColorValueCell(item: ColorValueItem) {
    val context = LocalContext.current
    val copied = stringResource(R.string.cp_color_value_copied)
    val copyLabel = stringResource(R.string.cp_copy_color_value_action, item.label)
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val copy = if (item.value != null) Modifier.combinedClickable(onClick = {}, onLongClickLabel = copyLabel,
        onLongClick = {
            clipboard?.setPrimaryClip(ClipData.newPlainText(item.label, item.value))
            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
        }) else Modifier
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 48.dp).then(copy).padding(vertical = 10.dp)) {
        val stacked = item.fullWidth || maxWidth < 240.dp || LocalDensity.current.fontScale > 1.3f
        val value = item.value ?: stringResource(R.string.colors_value_unavailable)
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(FuyaoSpacing.xs)) {
            Text(item.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        } else Row(horizontalArrangement = Arrangement.spacedBy(FuyaoSpacing.compact), verticalAlignment = Alignment.Top) {
            Text(item.label, Modifier.weight(.4f), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, Modifier.weight(.6f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
        }
    }
}
