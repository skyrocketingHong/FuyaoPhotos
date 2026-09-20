package ing.fuyaoskyrocket.photoinfo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.model.EditorSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: EditorSettings, hasPhoto: Boolean, onBack: () -> Unit, onSave: (EditorSettings, Boolean) -> Unit) {
    var author by rememberSaveable { mutableStateOf(settings.defaultAuthor) }
    var geocode by rememberSaveable { mutableStateOf(settings.resolvePhotoLocation) }
    var mainFocal by rememberSaveable { mutableStateOf(settings.fallbackMainFocal) }
    var lenses by remember { mutableStateOf(settings.lenses) }
    var manageLenses by rememberSaveable { mutableStateOf(false) }
    val draft = EditorSettings(author, geocode, mainFocal, lenses)
    if (manageLenses) {
        LensProfilesScreen(lenses, onBack = { manageLenses = false }, onSave = { onSave(draft.copy(lenses = it), false) })
        return
    }
    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.settings)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            actions = { TextButton(onClick = { onSave(draft, false) }, enabled = draft.validFocal) { Text(stringResource(R.string.save)) } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.default_author), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(author, onValueChange = { if (it.length <= 512) author = it },
                label = { Text(stringResource(R.string.field_author)) }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
            Text(stringResource(R.string.default_author_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { onSave(draft, true) }, enabled = hasPhoto && draft.validFocal) {
                Text(stringResource(R.string.save_apply_author))
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.resolve_location), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(geocode, onCheckedChange = { geocode = it })
            }
            Text(stringResource(R.string.resolve_location_hint), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(stringResource(R.string.lens_settings), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.lens_settings_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { manageLenses = true }, enabled = draft.validFocal) { Text(stringResource(R.string.manage_lenses, lenses.size)) }
            OutlinedTextField(mainFocal, onValueChange = { if (it.length <= 12) mainFocal = it },
                label = { Text(stringResource(R.string.main_focal)) }, placeholder = { Text("24") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = !draft.validFocal,
                supportingText = { Text(stringResource(if (draft.validFocal) R.string.main_focal_hint else R.string.main_focal_error)) })
        }
    }
}
