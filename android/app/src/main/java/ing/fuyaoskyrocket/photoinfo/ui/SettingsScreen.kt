package ing.fuyaoskyrocket.photoinfo.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.selection.toggleable
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
import ing.fuyaoskyrocket.photoinfo.ui.components.AboutContent
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
fun SettingsScreen(settings:EditorSettings,hasPhoto:Boolean,canSave:Boolean=true,onManageLenses:()->Unit,onBack:()->Unit,
    onDirtyChanged:(Boolean)->Unit,onSave:(EditorSettings,Boolean)->Unit) {
    var author by rememberSaveable { mutableStateOf(settings.defaultAuthor) }
    var geocode by rememberSaveable { mutableStateOf(settings.resolvePhotoLocation) }
    var mainFocal by rememberSaveable { mutableStateOf(settings.fallbackMainFocal) }
    var exportDefaults by rememberSaveable(stateSaver=ExportOptionsSaver) { mutableStateOf(settings.exportDefaults) }
    var hevcEncoder by rememberSaveable { mutableStateOf(settings.hevcEncoder) }
    var sharesCards by rememberSaveable { mutableStateOf(settings.metadataSharesCards) }
    var selectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.CARDS) }
    val draft=EditorSettings(author,geocode,mainFocal,settings.lenses,exportDefaults,hevcEncoder,sharesCards)
    val changed = ing.fuyaoskyrocket.photoinfo.domain.session.EditChanges.form(
        listOf(settings.defaultAuthor, settings.resolvePhotoLocation.toString(), settings.fallbackMainFocal),
        listOf(author, geocode.toString(), mainFocal), setOf(2)) || exportDefaults != settings.exportDefaults ||
        hevcEncoder != settings.hevcEncoder || sharesCards != settings.metadataSharesCards
    SideEffect { onDirtyChanged(changed) }
    rememberConfirmedBack(onBack, hasChanges = changed)

    @Composable fun overview() {
        FuyaoPageIntro(stringResource(R.string.settings), stringResource(R.string.settings_overview_description), R.drawable.ic_settings)
    }

    @Composable fun categoryContent(category: SettingsCategory) {
        when(category) {
            SettingsCategory.CARDS -> SettingsSection(stringResource(R.string.photo_cards_title),
                stringResource(R.string.settings_cards_description)) {
                OutlinedTextField(author,{ if(it.length<=512)author=it },Modifier.fillMaxWidth(),
                    label={ Text(stringResource(R.string.field_author)) },maxLines=3,
                    supportingText={ Text(stringResource(R.string.default_author_hint)) })
                TextButton(onClick={ onSave(draft,true) },enabled=canSave&&hasPhoto&&draft.validFocal) {
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
                Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(value=sharesCards,role=Role.Switch,
                    onValueChange={ sharesCards=it }),verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(R.string.metadata_shares_cards),Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                    Switch(sharesCards,onCheckedChange=null)
                }
                Text(stringResource(R.string.metadata_shares_cards_hint),style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
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
                TextButton(onClick=onManageLenses,contentPadding=PaddingValues(0.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Text(stringResource(R.string.manage_lenses,settings.lenses.size),Modifier.weight(1f),
                        style=MaterialTheme.typography.bodyLarge)
                    Icon(painterResource(R.drawable.ic_chevron),null)
                }
                OutlinedTextField(mainFocal,{ if(it.length<=12)mainFocal=it },Modifier.fillMaxWidth(),
                    label={ Text(stringResource(R.string.main_focal)) },
                    placeholder={ Text(stringResource(R.string.focal_example)) },singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                    isError=!draft.validFocal,
                    supportingText={ Text(stringResource(if(draft.validFocal)R.string.main_focal_hint else R.string.main_focal_error)) })
            }
            SettingsCategory.ABOUT -> SettingsSection(stringResource(R.string.about)) {
                AboutContent()
            }
        }
    }

    FuyaoScaffold("",showTopBar=false,bottomBar={
        if(changed) Surface(color=MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.safeDrawing).only(WindowInsetsSides.Bottom+WindowInsetsSides.Horizontal))
                .padding(horizontal=FuyaoSpacing.content,vertical=8.dp),horizontalArrangement=Arrangement.End) {
                Button(onClick={ onSave(draft,false) },enabled=canSave&&draft.validFocal) { Text(stringResource(R.string.save_settings)) }
            }
        }
    }) { padding ->
        FuyaoAdaptivePage(padding,contentUnderTopEdge=true,
            single = { modifier ->
                    FuyaoPageColumn(modifier) {
                        overview()
                        SettingsCategory.entries.forEach { categoryContent(it) }
                    }
            },
            leading = { modifier ->
                FuyaoPageColumn(modifier) {
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
                FuyaoPageColumn(modifier) {
                    categoryContent(selectedCategory)
                }
            })
    }
}

@Composable
private fun SettingsSection(title:String,description:String?=null,content:@Composable ColumnScope.()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        SectionHeading(title,description,Modifier.padding(horizontal=FuyaoSpacing.cardInset))
        Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,
            color=MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(FuyaoSpacing.cardInset),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
        }
    }
}
