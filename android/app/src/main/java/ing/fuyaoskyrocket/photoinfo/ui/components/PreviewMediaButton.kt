package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewMediaButton(icon: Int, label: String, checked: Boolean, onClick: () -> Unit,
    enabled: Boolean = true) {
    TooltipBox(positionProvider=TooltipDefaults.rememberTooltipPositionProvider(positioning=TooltipAnchorPosition.Above),
        tooltip={ PlainTooltip { Text(label) } },state=rememberTooltipState()) {
        FilledIconToggleButton(checked=checked,onCheckedChange={ onClick() },enabled=enabled,modifier=Modifier.size(48.dp),
            colors=IconButtonDefaults.iconToggleButtonColors(containerColor=Color.Transparent,
                contentColor=MaterialTheme.colorScheme.onSurface,
                checkedContainerColor=MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor=MaterialTheme.colorScheme.onPrimaryContainer,
                disabledContainerColor=if(checked)MaterialTheme.colorScheme.onSurface.copy(alpha=.12f) else Color.Transparent)) {
            Icon(painterResource(icon),label,Modifier.size(24.dp))
        }
    }
}
