package ing.fuyaoskyrocket.photoinfo.features.colors.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.features.colors.ui.theme.FuyaoDimensions
import ing.fuyaoskyrocket.photoinfo.features.colors.ui.theme.FuyaoSpacing

/** One copyable color representation; long syntax values can span the full panel width. */
internal data class ColorValueItem(
    val label: String,
    val value: String?,
    val fullWidth: Boolean = false,
    val valueMaxLines: Int = 2,
)

/** Adaptive, fixed-height value region shared by all color-space tabs. */
@Composable
internal fun ColorValuePanel(
    items: List<ColorValueItem>,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useTwoColumns = maxWidth >= 272.dp
        val rows = buildList<List<ColorValueItem>> {
            var index = 0
            while (index < items.size) {
                val item = items[index]
                val next = items.getOrNull(index + 1)
                val canPair = useTwoColumns && !item.fullWidth && next?.fullWidth == false
                if (canPair) {
                    add(listOf(item, requireNotNull(next)))
                    index += 2
                } else {
                    add(listOf(item))
                    index += 1
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(FuyaoSpacing.small),
        ) {
            if (header != null) {
                item(contentType = "header") { header() }
            }
            items(
                count = rows.size,
                contentType = { "color_value_row" },
            ) { rowIndex ->
                val rowItems = rows[rowIndex]
                if (rowItems.size == 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FuyaoSpacing.small),
                    ) {
                        ColorValueCell(item = rowItems[0], modifier = Modifier.weight(1f))
                        ColorValueCell(item = rowItems[1], modifier = Modifier.weight(1f))
                    }
                } else {
                    ColorValueCell(item = rowItems[0], modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** A stable-height 1:4 label/value row with long-press copy. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColorValueCell(
    item: ColorValueItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.cp_color_value_copied)
    val copyActionLabel = stringResource(R.string.cp_copy_color_value_action, item.label)
    val clipboardManager = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    val shape = MaterialTheme.shapes.medium
    val copyModifier = if (item.value != null) {
        Modifier.combinedClickable(
            onClick = {},
            onLongClickLabel = copyActionLabel,
            onLongClick = {
                clipboardManager?.setPrimaryClip(
                    ClipData.newPlainText(item.label, item.value),
                )
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
            },
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .heightIn(min = FuyaoDimensions.colorValueMinHeight)
            .clip(shape)
            .then(copyModifier),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FuyaoDimensions.colorValueMinHeight)
                .padding(
                    horizontal = FuyaoSpacing.compact,
                    vertical = FuyaoSpacing.small,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.label,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.value.orEmpty(),
                modifier = Modifier.weight(4f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = item.valueMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
