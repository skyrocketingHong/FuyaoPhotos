package ing.fuyaoskyrocket.photoinfo.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.ui.components.LocalThemeRevealKeyboardCancel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SettingsChoiceRow(
    title: String,
    selected: T,
    options: Map<T, String>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val cancelThemeRevealForKeyboard = LocalThemeRevealKeyboardCancel.current
    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }
    @Composable fun label(modifier: Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (supportingText != null) {
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    @Composable fun choice(modifier: Modifier) {
        Box(modifier, contentAlignment = Alignment.CenterEnd) {
            TextButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.semantics {
                    contentDescription = title
                    stateDescription = options[selected].orEmpty()
                },
            ) {
                Text(
                    options[selected].orEmpty(),
                    modifier = Modifier.weight(1f, fill = false),
                    textAlign = TextAlign.End,
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
            }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false },
                modifier = Modifier.onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown) cancelThemeRevealForKeyboard()
                    false
                }) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        enabled = enabled,
                        trailingIcon = {
                            if (selected == value) Icon(painterResource(R.drawable.ic_check), null)
                        },
                        onClick = {
                            expanded = false
                            onSelect(value)
                        },
                    )
                }
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.CenterStart) {
        val maximumChoiceWidth = maxWidth * 0.55f
        if (maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.5f) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                label(Modifier.fillMaxWidth())
                choice(Modifier.align(Alignment.End))
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                label(Modifier.weight(1f))
                choice(Modifier.widthIn(max = maximumChoiceWidth))
            }
        }
    }
}
