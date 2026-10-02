package ing.fuyaoskyrocket.photoinfo.ui.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun FuyaoFormSection(title: String? = null, description: String? = null, modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(FuyaoSpacing.cardInset),
    verticalSpacing: Dp = FuyaoSpacing.compact,
    content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FuyaoSpacing.small)) {
        if (title != null) SectionHeading(title, description, Modifier.padding(horizontal = FuyaoSpacing.cardInset))
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(verticalSpacing), content = content)
        }
    }
}
