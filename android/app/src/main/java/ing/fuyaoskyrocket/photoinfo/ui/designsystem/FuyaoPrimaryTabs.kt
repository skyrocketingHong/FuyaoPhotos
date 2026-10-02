package ing.fuyaoskyrocket.photoinfo.ui.designsystem

import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun FuyaoPrimaryTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier) {
    PrimaryTabRow(selectedTabIndex = selected, modifier = modifier,
        containerColor = Color.Transparent, divider = {}) {
        labels.forEachIndexed { index, label ->
            Tab(selected = selected == index, onClick = { onSelect(index) }, text = { Text(label) })
        }
    }
}
