package ing.fuyaoskyrocket.photoinfo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import ing.fuyaoskyrocket.photoinfo.ui.components.rememberConfirmedBack
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.BuildConfig
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.model.EditorSettings
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.*

@Composable
fun SettingsScreen(settings:EditorSettings,hasPhoto:Boolean,onManageLenses:()->Unit,onBack:()->Unit,onSave:(EditorSettings,Boolean)->Unit) {
    var author by rememberSaveable { mutableStateOf(settings.defaultAuthor) }
    var geocode by rememberSaveable { mutableStateOf(settings.resolvePhotoLocation) }
    var mainFocal by rememberSaveable { mutableStateOf(settings.fallbackMainFocal) }
    val draft=EditorSettings(author,geocode,mainFocal,settings.lenses)
    val changed = ing.fuyaoskyrocket.photoinfo.domain.session.EditChanges.form(
        listOf(settings.defaultAuthor, settings.resolvePhotoLocation.toString(), settings.fallbackMainFocal),
        listOf(author, geocode.toString(), mainFocal), setOf(2))
    val requestBack = rememberConfirmedBack(onBack, hasChanges = changed)
    FuyaoScaffold(stringResource(R.string.settings),onBack=requestBack,actions={
        TextButton(onClick={ onSave(draft,false) },enabled=draft.validFocal) { Text(stringResource(R.string.save)) }
    }) { padding ->
        FuyaoFormPage(padding) {
            SectionHeading(stringResource(R.string.default_author),stringResource(R.string.default_author_hint))
            OutlinedTextField(author,{ if(it.length<=512)author=it },Modifier.fillMaxWidth(),label={ Text(stringResource(R.string.field_author)) },maxLines=3)
            TextButton(onClick={ onSave(draft,true) },enabled=hasPhoto&&draft.validFocal) { Text(stringResource(R.string.save_apply_author)) }
            HorizontalDivider()
            SectionHeading(stringResource(R.string.section_metadata))
            val locationLabel=stringResource(R.string.resolve_location)
            Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(value=geocode,role=Role.Switch,onValueChange={ geocode=it }),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text(locationLabel,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                Switch(geocode,onCheckedChange=null)
            }
            Text(stringResource(R.string.resolve_location_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            SectionHeading(stringResource(R.string.lens_settings))
            ListItem(headlineContent={ Text(stringResource(R.string.manage_lenses,settings.lenses.size)) },
                supportingContent={ Text(stringResource(R.string.lens_entry_hint)) },
                trailingContent={ Icon(painterResource(R.drawable.ic_chevron),null) },
                modifier=Modifier.clickable(enabled=draft.validFocal,onClick=onManageLenses),
                colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow))
            OutlinedTextField(mainFocal,{ if(it.length<=12)mainFocal=it },Modifier.fillMaxWidth(),label={ Text(stringResource(R.string.main_focal)) },
                placeholder={ Text("24") },singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                isError=!draft.validFocal,supportingText={ Text(stringResource(if(draft.validFocal)R.string.main_focal_hint else R.string.main_focal_error)) })
            HorizontalDivider()
            SectionHeading(stringResource(R.string.about))
            Text("Fuyao Photo Info ${BuildConfig.MARKETING_VERSION} (${BuildConfig.BUILD_NUMBER})",style=MaterialTheme.typography.bodyMedium)
            Text("${BuildConfig.BUILD_TYPE} · AGPL-3.0-only",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
