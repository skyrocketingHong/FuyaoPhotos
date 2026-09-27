package ing.fuyaoskyrocket.photoinfo.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoScaffold
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoPageColumn
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoPageIntro

@Composable
fun PhotoMapScreen(modifier: Modifier = Modifier, onOpenMetadata: () -> Unit) {
    FuyaoScaffold(title = "", modifier = modifier, showTopBar = false) { padding ->
        FuyaoPageColumn(Modifier.fillMaxSize(),topInset=padding.calculateTopPadding()) {
            FuyaoPageIntro(stringResource(R.string.photo_map_title),
                stringResource(R.string.photo_map_reserved_body),R.drawable.ic_map) {
                TextButton(onClick = onOpenMetadata) {
                    Text(stringResource(R.string.photo_metadata_title))
                }
            }
        }
    }
}
