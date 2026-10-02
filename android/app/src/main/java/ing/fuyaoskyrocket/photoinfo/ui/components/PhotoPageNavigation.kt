package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R

@Composable
fun BoxScope.PhotoPageNavigation(count: Int, index: Int, busy: Boolean, select: (Int) -> Unit) {
    if (count <= 1) return
    Row(Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        FilledTonalIconButton({ select(index - 1) }, enabled = !busy && index > 0) {
            Icon(painterResource(R.drawable.ic_back), stringResource(R.string.previous_photo))
        }
        FilledTonalIconButton({ select(index + 1) }, enabled = !busy && index < count - 1) {
            Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.next_photo))
        }
    }
    Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(stringResource(R.string.photo_position, index + 1, count), Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium)
    }
}
