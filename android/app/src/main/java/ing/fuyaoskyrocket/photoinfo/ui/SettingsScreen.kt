package ing.fuyaoskyrocket.photoinfo.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.model.EditorSettings
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoSharing
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature
import ing.fuyaoskyrocket.photoinfo.domain.model.StartPage
import ing.fuyaoskyrocket.photoinfo.ui.components.AboutContent
import ing.fuyaoskyrocket.photoinfo.ui.components.ExportOptionsControls
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.*
import ing.fuyaoskyrocket.photoinfo.ui.theme.LocalPhotoMotionEnabled
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoMotionTokens
import ing.fuyaoskyrocket.photoinfo.ui.components.ThemeReveal
import ing.fuyaoskyrocket.photoinfo.ui.components.navigationEnter
import ing.fuyaoskyrocket.photoinfo.ui.components.navigationExit
import androidx.lifecycle.Lifecycle

private enum class SettingsCategory(@StringRes val label: Int, @StringRes val description: Int, @DrawableRes val icon: Int) {
    CARDS(R.string.photo_cards_title, R.string.settings_cards_description, R.drawable.ic_photo_add),
    METADATA(R.string.section_metadata, R.string.settings_metadata_description, R.drawable.ic_info),
    EXPORT(R.string.export_defaults, R.string.settings_export_description, R.drawable.ic_export),
    LENSES(R.string.lens_settings, R.string.settings_lenses_description, R.drawable.ic_photo_info),
    THEME(R.string.theme_settings, R.string.theme_settings_description, R.drawable.cp_ic_colorize),
    WORKSPACE(R.string.workspace_header, R.string.settings_workspace_description, R.drawable.ic_settings),
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SettingsScreen(settings:EditorSettings,hasPhoto:Boolean,enabled:Boolean=true,onManageLenses:()->Unit,
    onUpdate:((EditorSettings)->EditorSettings)->Unit,onApplyAuthor:()->Unit) {
    var author by rememberSaveable { mutableStateOf(settings.defaultAuthor) }
    var mainFocal by rememberSaveable { mutableStateOf(settings.fallbackMainFocal) }
    val geocode = settings.resolvePhotoLocation
    val preferLensPixelCount = settings.preferLensPixelCount
    val exportDefaults = settings.exportDefaults
    val hevcEncoder = settings.hevcEncoder
    val workspace = settings.workspace
    val startPage = workspace.startPage
    val sharing = workspace.sharing
    val validMainFocal = settings.copy(fallbackMainFocal = mainFocal).validFocal
    var selectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.CARDS) }
    val categoryNavigation = rememberNavController()
    val motionEnabled = LocalPhotoMotionEnabled.current
    val containerMotion = motionEnabled && settings.workspace.predictiveBackStyle != ing.fuyaoskyrocket.photoinfo.domain.model.PredictiveBackStyle.NONE

    ThemeReveal { changeTheme ->

    @Composable fun overview() {
        FuyaoPageIntro(stringResource(R.string.settings), stringResource(R.string.settings_overview_description), R.drawable.ic_settings)
    }

    @Composable fun categoryContent(category: SettingsCategory, canEdit: () -> Boolean = { enabled }) {
        FuyaoPageIntro(stringResource(category.label), stringResource(category.description), category.icon)
        when(category) {
            SettingsCategory.THEME -> ThemeSettingsContent(workspace, enabled = enabled) { update ->
                if (canEdit()) changeTheme { onUpdate { current -> current.copy(workspace = update(current.workspace)) } }
            }
            SettingsCategory.WORKSPACE -> FuyaoFormSection {
                SettingsChoiceRow(stringResource(R.string.workspace_startup), startPage,
                    StartPage.entries.associateWith { stringResource(when(it) {
                        StartPage.MAP -> R.string.photo_map_title; StartPage.EDITOR -> R.string.photo_cards_title
                        StartPage.METADATA -> R.string.photo_metadata_title; StartPage.COLORS -> R.string.colors_title
                    }) }, enabled = enabled) { value ->
                        if (canEdit()) onUpdate { it.copy(workspace = it.workspace.copy(startPage = value)) }
                    }
                SettingsChoiceRow(stringResource(R.string.workspace_sharing), sharing,
                    PhotoSharing.entries.associateWith { stringResource(when(it) {
                        PhotoSharing.ALL -> R.string.workspace_sharing_all; PhotoSharing.INDEPENDENT -> R.string.workspace_sharing_none
                        PhotoSharing.PARTIAL -> R.string.workspace_sharing_partial
                    }) }, enabled = enabled) { value ->
                        if (canEdit()) onUpdate { it.copy(workspace = it.workspace.copy(sharing = value)) }
                    }
                AnimatedVisibility(visible = sharing == PhotoSharing.PARTIAL,
                    enter = if (motionEnabled) fadeIn(tween(180)) + expandVertically(tween(200)) else EnterTransition.None,
                    exit = if (motionEnabled) fadeOut(tween(120)) + shrinkVertically(tween(180)) else ExitTransition.None) {
                Column {
                PhotoFeature.entries.forEach { feature ->
                    val selected = feature in workspace.sharedFeatures
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(selected, enabled = enabled && sharing == PhotoSharing.PARTIAL, role=Role.Checkbox,
                        onValueChange={ checked -> if (canEdit()) onUpdate {
                            it.copy(workspace = it.workspace.copy(sharedFeatures = if (checked)
                                it.workspace.sharedFeatures + feature else it.workspace.sharedFeatures - feature))
                        } }),
                        verticalAlignment=Alignment.CenterVertically) {
                        Text(stringResource(when(feature) {
                            PhotoFeature.CARDS -> R.string.photo_cards_title; PhotoFeature.METADATA -> R.string.photo_metadata_title
                            PhotoFeature.COLORS -> R.string.colors_title
                        }), Modifier.weight(1f))
                        Checkbox(selected, onCheckedChange=null, enabled=enabled)
                    }
                }
                }
                }
                Text(stringResource(R.string.workspace_sharing_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsCategory.CARDS -> FuyaoFormSection {
                OutlinedTextField(author,{ value -> if(canEdit() && value.length<=512) {
                    author=value
                    onUpdate { it.copy(defaultAuthor=value) }
                } },Modifier.fillMaxWidth(), enabled=enabled,
                    label={ Text(stringResource(R.string.field_author)) },maxLines=3,
                    supportingText={ Text(stringResource(R.string.default_author_hint)) })
                TextButton(onClick={ if (canEdit()) onApplyAuthor() },enabled=enabled&&hasPhoto) {
                    Text(stringResource(R.string.apply_author_to_photo))
                }
                Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(value=preferLensPixelCount, enabled=enabled, role=Role.Switch,
                    onValueChange={ value -> if (canEdit()) onUpdate { it.copy(preferLensPixelCount=value) } }), verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(R.string.prefer_lens_pixel_count), Modifier.weight(1f), style=MaterialTheme.typography.bodyLarge)
                    Switch(preferLensPixelCount, onCheckedChange=null, enabled=enabled)
                }
                Text(stringResource(R.string.prefer_lens_pixel_count_hint), style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsCategory.EXPORT -> FuyaoFormSection {
                ExportOptionsControls(exportDefaults,{ value -> if (canEdit()) onUpdate { it.copy(exportDefaults=value.photoSave()) } }, enabled=enabled)
                Text(stringResource(R.string.export_defaults_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                val x265Available=ing.fuyaoskyrocket.photoinfo.platform.HevcEncoders.x265Available
                val encoderLabel=stringResource(R.string.hevc_encoder)
                Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(
                        value=hevcEncoder==ing.fuyaoskyrocket.photoinfo.platform.HevcEncoderKind.X265,
                        enabled=enabled&&x265Available,role=Role.Switch,
                        onValueChange={ value -> if (canEdit()) onUpdate { it.copy(hevcEncoder=if(value)
                            ing.fuyaoskyrocket.photoinfo.platform.HevcEncoderKind.X265
                            else ing.fuyaoskyrocket.photoinfo.platform.HevcEncoderKind.PLATFORM) } }),
                    verticalAlignment=Alignment.CenterVertically) {
                    Text(encoderLabel,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                    Switch(hevcEncoder==ing.fuyaoskyrocket.photoinfo.platform.HevcEncoderKind.X265,
                        onCheckedChange=null,enabled=enabled&&x265Available)
                }
                Text(stringResource(if(x265Available)R.string.hevc_encoder_hint else R.string.hevc_encoder_unavailable),
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsCategory.METADATA -> FuyaoFormSection {
                val locationLabel=stringResource(R.string.resolve_location)
                Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(value=geocode,enabled=enabled,role=Role.Switch,
                    onValueChange={ value -> if (canEdit()) onUpdate { it.copy(resolvePhotoLocation=value) } }),verticalAlignment=Alignment.CenterVertically) {
                    Text(locationLabel,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                    Switch(geocode,onCheckedChange=null,enabled=enabled)
                }
                Text(stringResource(R.string.resolve_location_hint),style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsCategory.LENSES -> FuyaoFormSection {
                TextButton(onClick={ if (canEdit()) onManageLenses() },enabled=enabled,contentPadding=PaddingValues(0.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Text(stringResource(R.string.manage_lenses,settings.lenses.size),Modifier.weight(1f),
                        style=MaterialTheme.typography.bodyLarge)
                    Icon(painterResource(R.drawable.ic_chevron),null)
                }
                OutlinedTextField(mainFocal,{ value -> if(canEdit() && value.length<=12) {
                    mainFocal=value
                    if (settings.copy(fallbackMainFocal=value).validFocal) onUpdate { it.copy(fallbackMainFocal=value) }
                } },Modifier.fillMaxWidth(), enabled=enabled,
                    label={ Text(stringResource(R.string.main_focal)) },
                    placeholder={ Text(stringResource(R.string.focal_example)) },singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                    isError=!validMainFocal,
                    supportingText={ Text(stringResource(if(validMainFocal)R.string.main_focal_hint else R.string.main_focal_error)) })
            }
        }
    }

    FuyaoScaffold("",showTopBar=false) { padding ->
        FuyaoAdaptivePage(padding,contentUnderTopEdge=true,leadingPaneWidth=280.dp,
            single = { modifier ->
                SharedTransitionLayout(modifier) {
                NavHost(categoryNavigation, startDestination = "overview", modifier = Modifier.fillMaxSize(),
                    enterTransition = { navigationEnter(settings.workspace.predictiveBackStyle, motionEnabled) },
                    exitTransition = { navigationExit(settings.workspace.predictiveBackStyle, motionEnabled) },
                    popEnterTransition = { navigationEnter(settings.workspace.predictiveBackStyle, motionEnabled, pop = true) },
                    popExitTransition = { navigationExit(settings.workspace.predictiveBackStyle, motionEnabled, pop = true) }) {
                    composable("overview") { entry ->
                        val visibility = this
                        FuyaoPageColumn {
                            overview()
                            FuyaoFormSection(contentPadding = PaddingValues(4.dp), verticalSpacing = 0.dp) {
                                SettingsCategory.entries.forEach { category ->
                                    ListItem(
                                        headlineContent = { Text(stringResource(category.label)) },
                                        leadingContent = { Icon(painterResource(category.icon), null) },
                                        trailingContent = { Icon(painterResource(R.drawable.ic_chevron), null) },
                                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                        modifier = Modifier.fillMaxWidth().then(if (containerMotion) Modifier.sharedBounds(
                                            rememberSharedContentState("settings-${category.name}"), visibility,
                                            boundsTransform = { _, _ -> tween(PhotoMotionTokens.containerMillis, easing = FastOutSlowInEasing) },
                                            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopStart),
                                            clipInOverlayDuringTransition = OverlayClip(MaterialTheme.shapes.extraLarge),
                                        ) else Modifier).clickable {
                                            if (entry.lifecycle.currentState == Lifecycle.State.RESUMED &&
                                                categoryNavigation.currentBackStackEntry == entry) {
                                                selectedCategory = category
                                                categoryNavigation.navigate("category") { launchSingleTop = true }
                                            }
                                        },
                                    )
                                }
                            }
                            FuyaoFormSection(stringResource(R.string.about)) { AboutContent() }
                        }
                    }
                    composable("category") { entry ->
                        val container = if (containerMotion) Modifier.sharedBounds(
                            rememberSharedContentState("settings-${selectedCategory.name}"), this,
                            boundsTransform = { _, _ -> tween(PhotoMotionTokens.containerMillis, easing = FastOutSlowInEasing) },
                            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopStart),
                            clipInOverlayDuringTransition = OverlayClip(MaterialTheme.shapes.extraLarge),
                        ) else Modifier
                        Box(container.fillMaxSize()) {
                        FuyaoScaffold(stringResource(selectedCategory.label),
                            additionalTopInset = LocalPaneTopInset.current,
                            onBack = {
                                if (entry.lifecycle.currentState == Lifecycle.State.RESUMED &&
                                    categoryNavigation.currentBackStackEntry == entry) categoryNavigation.popBackStack()
                            }) { categoryPadding ->
                            FuyaoPageColumn(Modifier.fillMaxSize().consumeWindowInsets(categoryPadding),
                                topInset = categoryPadding.calculateTopPadding()) {
                                categoryContent(selectedCategory) {
                                    enabled && entry.lifecycle.currentState == Lifecycle.State.RESUMED && categoryNavigation.currentBackStackEntry == entry
                                }
                            }
                        }
                        }
                    }
                }
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
                    FuyaoFormSection(stringResource(R.string.about)) { AboutContent() }
                }
            },
            trailing = { modifier ->
                key(selectedCategory) {
                    FuyaoPageColumn(modifier) {
                        categoryContent(selectedCategory)
                    }
                }
            })
    }
    }
}
