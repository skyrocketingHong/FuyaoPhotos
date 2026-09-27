package ing.fuyaoskyrocket.photoinfo.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.model.EditorSettings
import ing.fuyaoskyrocket.photoinfo.ui.components.AboutDialog
import ing.fuyaoskyrocket.photoinfo.ui.components.ExportOptionsControls
import ing.fuyaoskyrocket.photoinfo.ui.components.ExportOptionsSaver
import ing.fuyaoskyrocket.photoinfo.ui.components.rememberConfirmedBack
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.*

private enum class SettingsCategory(@StringRes val label: Int, @DrawableRes val icon: Int) {
    CARDS(R.string.photo_cards_title, R.drawable.ic_photo_add),
    EXPORT(R.string.export_defaults, R.drawable.ic_export),
    METADATA(R.string.section_metadata, R.drawable.ic_info),
    LENSES(R.string.lens_settings, R.drawable.ic_photo_info),
    ABOUT(R.string.about, R.drawable.ic_info),
}

@Composable
fun SettingsScreen(settings:EditorSettings,hasPhoto:Boolean,onManageLenses:()->Unit,onBack:()->Unit,
    onDirtyChanged:(Boolean)->Unit,onSave:(EditorSettings,Boolean)->Unit) {
    var showAbout by rememberSaveable { mutableStateOf(false) }
    if(showAbout)AboutDialog { showAbout=false }
    var author by rememberSaveable { mutableStateOf(settings.defaultAuthor) }
    var geocode by rememberSaveable { mutableStateOf(settings.resolvePhotoLocation) }
    var mainFocal by rememberSaveable { mutableStateOf(settings.fallbackMainFocal) }
    var exportDefaults by rememberSaveable(stateSaver=ExportOptionsSaver) { mutableStateOf(settings.exportDefaults) }
    var hevcEncoder by rememberSaveable { mutableStateOf(settings.hevcEncoder) }
    var selectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.CARDS) }
    val draft=EditorSettings(author,geocode,mainFocal,settings.lenses,exportDefaults,hevcEncoder)
    val changed = ing.fuyaoskyrocket.photoinfo.domain.session.EditChanges.form(
        listOf(settings.defaultAuthor, settings.resolvePhotoLocation.toString(), settings.fallbackMainFocal),
        listOf(author, geocode.toString(), mainFocal), setOf(2)) || exportDefaults != settings.exportDefaults ||
        hevcEncoder != settings.hevcEncoder
    SideEffect { onDirtyChanged(changed) }
    val requestBack = rememberConfirmedBack(onBack, hasChanges = changed, enabled = !showAbout)

    @Composable fun overview() {
        Surface(Modifier.fillMaxWidth(), shape=MaterialTheme.shapes.extraLarge,
            color=MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_settings),null,Modifier.size(32.dp),
                    tint=MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.settings),style=MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.settings_overview_description),
                    style=MaterialTheme.typography.bodyMedium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable fun categoryContent(category: SettingsCategory) {
        when(category) {
            SettingsCategory.CARDS -> SettingsSection(stringResource(R.string.photo_cards_title),
                stringResource(R.string.settings_cards_description)) {
                OutlinedTextField(author,{ if(it.length<=512)author=it },Modifier.fillMaxWidth(),
                    label={ Text(stringResource(R.string.field_author)) },maxLines=3,
                    supportingText={ Text(stringResource(R.string.default_author_hint)) })
                TextButton(onClick={ onSave(draft,true) },enabled=hasPhoto&&draft.validFocal) {
                    Text(stringResource(R.string.save_apply_author))
                }
            }
            SettingsCategory.EXPORT -> SettingsSection(stringResource(R.string.export_defaults),
                stringResource(R.string.export_defaults_hint)) {
                ExportOptionsControls(exportDefaults,{ exportDefaults=it })
                HorizontalDivider()
                val x265Available=ing.fuyaoskyrocket.photoinfo.platform.HevcEncoders.x265Available
                val encoderLabel=stringResource(R.string.hevc_encoder)
                Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(
                        value=hevcEncoder==ing.fuyaoskyrocket.photoinfo.platform.HevcEncoderKind.X265,
                        enabled=x265Available,role=Role.Switch,
                        onValueChange={ hevcEncoder=if(it) ing.fuyaoskyrocket.photoinfo.platform.HevcEncoderKind.X265
                            else ing.fuyaoskyrocket.photoinfo.platform.HevcEncoderKind.PLATFORM }),
                    verticalAlignment=Alignment.CenterVertically) {
                    Text(encoderLabel,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                    Switch(hevcEncoder==ing.fuyaoskyrocket.photoinfo.platform.HevcEncoderKind.X265,
                        onCheckedChange=null,enabled=x265Available)
                }
                Text(stringResource(if(x265Available)R.string.hevc_encoder_hint else R.string.hevc_encoder_unavailable),
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsCategory.METADATA -> SettingsSection(stringResource(R.string.section_metadata),
                stringResource(R.string.settings_metadata_description)) {
                val locationLabel=stringResource(R.string.resolve_location)
                Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(value=geocode,role=Role.Switch,
                    onValueChange={ geocode=it }),verticalAlignment=Alignment.CenterVertically) {
                    Text(locationLabel,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                    Switch(geocode,onCheckedChange=null)
                }
                Text(stringResource(R.string.resolve_location_hint),style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsCategory.LENSES -> SettingsSection(stringResource(R.string.lens_settings),
                stringResource(R.string.lens_entry_hint)) {
                ListItem(headlineContent={ Text(stringResource(R.string.manage_lenses,settings.lenses.size)) },
                    trailingContent={ Icon(painterResource(R.drawable.ic_chevron),null) },
                    modifier=Modifier.clickable(onClick=onManageLenses),
                    colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow))
                OutlinedTextField(mainFocal,{ if(it.length<=12)mainFocal=it },Modifier.fillMaxWidth(),
                    label={ Text(stringResource(R.string.main_focal)) },
                    placeholder={ Text(stringResource(R.string.focal_example)) },singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                    isError=!draft.validFocal,
                    supportingText={ Text(stringResource(if(draft.validFocal)R.string.main_focal_hint else R.string.main_focal_error)) })
            }
            SettingsCategory.ABOUT -> SettingsSection(stringResource(R.string.about),
                stringResource(R.string.about_summary)) {
                ListItem(headlineContent={ Text(stringResource(R.string.about)) },
                    trailingContent={ Icon(painterResource(R.drawable.ic_chevron),null) },
                    modifier=Modifier.clickable { showAbout=true },
                    colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow))
            }
        }
    }

    FuyaoScaffold(stringResource(R.string.settings),onBack=requestBack,actions={
        TextButton(onClick={ onSave(draft,false) },enabled=draft.validFocal) { Text(stringResource(R.string.save_settings)) }
    }) { padding ->
        FuyaoAdaptivePage(padding,
            single = { modifier ->
                Box(modifier,contentAlignment=Alignment.TopCenter) {
                    Column(Modifier.widthIn(max=FuyaoLayout.readable).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        overview()
                        SettingsCategory.entries.forEach { categoryContent(it) }
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
                    }
                }
            },
            leading = { modifier ->
                Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    overview()
                    SettingsCategory.entries.forEach { category ->
                        NavigationDrawerItem(
                            label={ Text(stringResource(category.label)) },
                            selected=selectedCategory==category,
                            onClick={ selectedCategory=category },
                            icon={ Icon(painterResource(category.icon),null) },
                        )
                    }
                }
            },
            trailing = { modifier ->
                Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    categoryContent(selectedCategory)
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
                }
            })
    }
}

@Composable
private fun SettingsSection(title:String,description:String,content:@Composable ColumnScope.()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        SectionHeading(title,description)
        Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,
            color=MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
        }
    }
}
