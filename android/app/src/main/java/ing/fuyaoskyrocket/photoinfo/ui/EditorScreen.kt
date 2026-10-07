package ing.fuyaoskyrocket.photoinfo.ui

import android.Manifest
import android.net.Uri
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import android.content.Intent
import android.os.Build
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import ing.fuyaoskyrocket.photoinfo.ui.theme.LocalPhotoMotionEnabled
import ing.fuyaoskyrocket.photoinfo.ui.components.FuyaoBottomNavigation
import ing.fuyaoskyrocket.photoinfo.ui.components.NavigationDestination
import ing.fuyaoskyrocket.photoinfo.ui.components.NavigationBackdrop
import ing.fuyaoskyrocket.photoinfo.ui.components.LocalNavigationBackdrop
import ing.fuyaoskyrocket.photoinfo.ui.components.rememberNavigationBackdrop
import ing.fuyaoskyrocket.photoinfo.ui.components.navigationEnter
import ing.fuyaoskyrocket.photoinfo.ui.components.navigationExit
import ing.fuyaoskyrocket.photoinfo.ui.components.PhotoDissolveHost
import ing.fuyaoskyrocket.photoinfo.ui.components.rememberPhotoDissolveController
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature
import ing.fuyaoskyrocket.photoinfo.domain.model.WorkspaceSettings
import androidx.compose.ui.platform.LocalFocusManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import ing.fuyaoskyrocket.photoinfo.ui.components.SystemBackObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import ing.fuyaoskyrocket.photoinfo.platform.PhotoIntents
import ing.fuyaoskyrocket.photoinfo.ui.components.ExportNotice
import ing.fuyaoskyrocket.photoinfo.ui.components.ExportNoticeVisuals
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogProperties
import ing.fuyaoskyrocket.photoinfo.domain.session.PhotoEditSnapshot
import ing.fuyaoskyrocket.photoinfo.ui.components.rememberConfirmedBack
import kotlin.math.roundToInt
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.*
import ing.fuyaoskyrocket.photoinfo.ui.components.EditorWorkspace
import ing.fuyaoskyrocket.photoinfo.ui.components.PhotoWorkspaceCanvas
import ing.fuyaoskyrocket.photoinfo.ui.components.EditorPreviewPane
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import ing.fuyaoskyrocket.photoinfo.platform.PreviewDynamicRange
import ing.fuyaoskyrocket.photoinfo.platform.hasHdrPreviewContent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.data.export.PhotoExporter
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportOptions
import ing.fuyaoskyrocket.photoinfo.ui.components.ExportOptionsControls
import ing.fuyaoskyrocket.photoinfo.ui.components.ExportOptionsSaver
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportFormat
import ing.fuyaoskyrocket.photoinfo.presentation.EditorViewModel
import ing.fuyaoskyrocket.photoinfo.presentation.MetadataViewModel
import ing.fuyaoskyrocket.photoinfo.presentation.originalPhoto
import ing.fuyaoskyrocket.photoinfo.ui.components.OriginalPreviewState
import ing.fuyaoskyrocket.photoinfo.ui.components.OriginalPreviewMode
import ing.fuyaoskyrocket.photoinfo.ui.components.EditorControls
import ing.fuyaoskyrocket.photoinfo.ui.components.exportBlockingNotice
import ing.fuyaoskyrocket.photoinfo.ui.components.FullScreenPreview
import ing.fuyaoskyrocket.photoinfo.ui.map.PhotoMapScreen

private enum class PhotoPage { MAP, EDITOR, METADATA, COLORS, SETTINGS, LENSES, LENS_EDIT, PREVIEW }

private data class PhotoTab(val page: PhotoPage, @StringRes val label: Int, @DrawableRes val icon: Int)
private val topLevelTabs = listOf(
    PhotoTab(PhotoPage.MAP, R.string.photo_map_title, R.drawable.ic_map),
    PhotoTab(PhotoPage.EDITOR, R.string.photo_cards_title, R.drawable.ic_photo_add),
    PhotoTab(PhotoPage.METADATA, R.string.photo_metadata_title, R.drawable.ic_info),
    PhotoTab(PhotoPage.COLORS, R.string.colors_title, R.drawable.cp_ic_colorize),
    PhotoTab(PhotoPage.SETTINGS, R.string.settings, R.drawable.ic_settings),
)
private val topLevelPages = topLevelTabs.map(PhotoTab::page)
private val navigationDestinations = topLevelTabs.map { NavigationDestination(it.label, it.icon) }

@Composable
private fun PhotoBottomBar(selected: PhotoPage, enabled: Boolean, backdrop: NavigationBackdrop, settings: WorkspaceSettings,
    onSelect: (PhotoPage) -> Unit) {
    FuyaoBottomNavigation(backdrop, settings, topLevelTabs.indexOfFirst { it.page == selected }.coerceAtLeast(0),
        navigationDestinations, enabled) { topLevelTabs.getOrNull(it)?.let { tab -> onSelect(tab.page) } }
}

@Composable
private fun PhotoSideRail(selected: PhotoPage, enabled: Boolean, onSelect: (PhotoPage) -> Unit) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        topLevelTabs.forEach { tab ->
            NavigationRailItem(
                selected = selected == tab.page,
                onClick = { onSelect(tab.page) },
                enabled = enabled,
                icon = { Icon(painterResource(tab.icon), contentDescription = null, Modifier.size(24.dp)) },
                label = { Text(stringResource(tab.label)) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(vm: EditorViewModel = viewModel(), onExit: () -> Unit = {}) {
    val state = vm.state
    val motion = LocalPhotoMotionEnabled.current
    val metadata: MetadataViewModel = viewModel()
    val metadataState = metadata.state
    val metadataEdits: ing.fuyaoskyrocket.photoinfo.presentation.MetadataEditViewModel = viewModel()
    val cardChanges = state.hasChanges || state.photos.any { metadataEdits.hasChanges(it.id) }
    val colors: ing.fuyaoskyrocket.photoinfo.presentation.ColorPhotosViewModel = viewModel()
    val colorState = colors.state
    val dust = rememberPhotoDissolveController()
    val departureEffects = vm.photoDepartureEffects
    DisposableEffect(departureEffects, dust) {
        val receiverOwner = Any()
        departureEffects.bind(receiverOwner, dust::dissolve)
        onDispose { departureEffects.unbind(receiverOwner) }
    }
    SideEffect {
        metadataEdits.setSourceAccessPolicy { id, path ->
            when (vm.state.settings.workspace.owner(PhotoFeature.METADATA)) {
                PhotoFeature.CARDS -> vm.state.let { current ->
                    !current.busy && current.photos.getOrNull(current.photoIndex)?.let {
                        it.id == id && it.path == path
                    } == true
                }
                PhotoFeature.METADATA -> metadata.state.let { current ->
                    !current.busy && current.current?.let { it.id == id && it.file.absolutePath == path } == true
                }
                PhotoFeature.COLORS -> colors.state.let { current ->
                    !current.busy && current.current?.let { it.id == id && it.file.absolutePath == path } == true
                }
            }
        }
    }
    val backdrop = rememberNavigationBackdrop(state.settings.workspace.blurNavigation || state.settings.workspace.glassNavigation)
    val focus = LocalFocusManager.current
    var closingFeature by remember { mutableStateOf<PhotoFeature?>(null) }
    fun photosFor(owner: PhotoFeature) = when (owner) {
        PhotoFeature.CARDS -> vm.state.photos
        PhotoFeature.METADATA -> metadata.state.photos
        PhotoFeature.COLORS -> colors.state.photos
    }
    fun dissolveID(feature: PhotoFeature) = when (feature) {
        PhotoFeature.CARDS -> "cards-photo"
        PhotoFeature.METADATA -> "metadata-photo"
        PhotoFeature.COLORS -> "colors-photo"
    }
    fun closePhotos(feature: PhotoFeature) {
        if (metadataEdits.busy || vm.state.exporting) return
        val owner = vm.state.settings.workspace.owner(feature)
        val ids = photosFor(owner).map { it.id }.toSet()
        focus.clearFocus(force = true)
        dust.dissolve(dissolveID(feature))
        metadataEdits.discard(ids)
        when (owner) {
            PhotoFeature.CARDS -> vm.closeSession(showProgress = false) { }
            PhotoFeature.METADATA -> metadata.closeSession()
            PhotoFeature.COLORS -> colors.closeSession()
        }
    }
    fun requestClose(feature: PhotoFeature) {
        if (metadataEdits.busy || vm.state.exporting) return
        val owner = vm.state.settings.workspace.owner(feature)
        if ((owner == PhotoFeature.CARDS && vm.state.hasChanges) || photosFor(owner).any { metadataEdits.hasChanges(it.id) }) closingFeature = feature
        else closePhotos(feature)
    }
    fun importCards(uris: List<Uri>, sourceID: String = "cards-photo") {
        val previous = vm.state.photos.map { it.id }.toSet()
        vm.importPhotos(uris) { departureEffects.dispatch(sourceID); metadataEdits.discard(previous) }
    }
    val startupPage = rememberSaveable { state.settings.workspace.startPage.name }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var showExportBlocked by rememberSaveable { mutableStateOf(false) }
    val activePhotoId = state.photos.getOrNull(state.photoIndex)?.id
    var editingPhotoText by remember(activePhotoId) { mutableStateOf(false) }
    var original by rememberSaveable { mutableStateOf(false) }
    var hdrEnabled by rememberSaveable { mutableStateOf(true) }
    val hasHdrContent=state.original.hasHdrPreviewContent()
    val hdrAvailable=hasHdrContent && LocalView.current.display?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty()==true
    var showPhotoMenu by remember { mutableStateOf(false) }
    val navigation = rememberNavController()
    val currentEntry by navigation.currentBackStackEntryAsState()
    var settingsDirty by remember { mutableStateOf(false) }
    var pendingTab by remember { mutableStateOf<PhotoPage?>(null) }
    var requestedTab by remember { mutableStateOf<PhotoPage?>(null) }
    var settingsLenses by rememberSaveable(stateSaver = ProfilesSaver) { mutableStateOf(state.settings.lenses) }
    fun atPage(page: PhotoPage) = navigation.currentBackStackEntry?.let {
        it.destination.route == page.name && it.lifecycle.currentState == Lifecycle.State.RESUMED
    } == true
    fun openPage(from: PhotoPage, to: PhotoPage) {
        if (atPage(from)) navigation.navigate(to.name) { launchSingleTop = true }
    }
    fun returnFrom(page: PhotoPage) { if (atPage(page)) navigation.popBackStack() }
    fun navigateToTab(page: PhotoPage) {
        val leavingSettings = currentEntry?.destination?.route == PhotoPage.SETTINGS.name
        navigation.navigate(page.name) {
            popUpTo(navigation.graph.startDestinationId) { saveState = !leavingSettings }
            launchSingleTop = true
            restoreState = page != PhotoPage.SETTINGS
        }
    }
    fun selectTab(page: PhotoPage) {
        if (page in topLevelPages) requestedTab = page
    }
    LaunchedEffect(requestedTab, currentEntry) {
        val target = requestedTab ?: return@LaunchedEffect
        val entry = currentEntry ?: return@LaunchedEffect
        // A tap during a transition waits for RESUMED instead of being discarded.
        entry.lifecycle.currentStateFlow.first { it == Lifecycle.State.RESUMED }
        if (requestedTab != target || navigation.currentBackStackEntry != entry) return@LaunchedEffect
        requestedTab = null
        if (entry.destination.route == target.name) return@LaunchedEffect
        if (entry.destination.route == PhotoPage.SETTINGS.name && settingsDirty) pendingTab = target
        else {
            if (target == PhotoPage.SETTINGS) settingsLenses = state.settings.lenses
            navigateToTab(target)
        }
    }
    var editingLens by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var editedLens by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var pendingPhotos by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val photoPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingPhotos?.let { values -> pendingPhotos = null; importCards(values.map(Uri::parse)) }
    }
    val importConfirmedPhotos: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            navigation.popBackStack(PhotoPage.EDITOR.name, false)
            if (Build.VERSION.SDK_INT >= 29 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                pendingPhotos = ArrayList(uris.map(Uri::toString))
                photoPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            } else importCards(uris)
        }
    }
    var replacementPhotos by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val importSelectedPhotos: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty() && !vm.state.busy && !metadataEdits.busy) {
            if (vm.state.hasChanges || vm.state.photos.any { metadataEdits.hasChanges(it.id) }) replacementPhotos = uris.map(Uri::toString)
            else importConfirmedPhotos(uris)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), importSelectedPhotos)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PhotoEditSnapshot.MAX_PHOTOS), importSelectedPhotos)
    var metadataPendingPhotos by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var metadataReplacement by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var metadataImportSharesCards by rememberSaveable { mutableStateOf(false) }
    var importFeature by rememberSaveable { mutableStateOf(ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.METADATA) }
    var importOwner by rememberSaveable { mutableStateOf(ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.METADATA) }
    fun importMetadataPhotos(uris: List<Uri>) {
        val sourceID = dissolveID(importFeature)
        val previous = photosFor(importOwner).map { it.id }.toSet()
        val replacing = { departureEffects.dispatch(sourceID); metadataEdits.discard(previous) }
        when (importOwner) {
            PhotoFeature.CARDS -> vm.importPhotos(uris, replacing)
            PhotoFeature.METADATA -> metadata.importPhotos(uris, replacing)
            PhotoFeature.COLORS -> colors.importPhotos(uris, replacing)
        }
    }
    val metadataPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        metadataPendingPhotos?.let { values ->
            metadataPendingPhotos = null
            importMetadataPhotos(values.map(Uri::parse))
        }
    }
    val confirmMetadataImport: (List<Uri>) -> Unit = { uris ->
        if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            metadataPendingPhotos = uris.map(Uri::toString)
            metadataPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        } else importMetadataPhotos(uris)
    }
    val selectMetadataPhotos: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            importOwner = vm.state.settings.workspace.owner(importFeature)
            metadataImportSharesCards = importOwner == ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.CARDS
            val replaced = when (importOwner) {
                ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.CARDS -> vm.state.photos
                ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.METADATA -> metadata.state.photos
                ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.COLORS -> colors.state.photos
            }
            if ((metadataImportSharesCards && vm.state.hasChanges) || replaced.any { metadataEdits.hasChanges(it.id) }) metadataReplacement = uris.map(Uri::toString)
            else confirmMetadataImport(uris)
        }
    }
    val metadataGallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(PhotoEditSnapshot.MAX_PHOTOS), selectMetadataPhotos)
    val metadataFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), selectMetadataPhotos)
    val captureColorPhoto = ing.fuyaoskyrocket.photoinfo.features.colors.rememberColorCamera(selectMetadataPhotos)
    var folderFormat by rememberSaveable { mutableStateOf(ExportFormat.JPEG.name) }
    val exportFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.export(ExportFormat.valueOf(folderFormat), directory = uri)
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importFont) }
    val jpegDestination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) {
        if (it != null) vm.export(ExportFormat.JPEG, it)
    }
    val pngDestination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) {
        if (it != null) vm.export(ExportFormat.PNG, it)
    }
    val heicDestination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/heic")) {
        if (it != null) vm.export(ExportFormat.HEIC, it)
    }
    val shareLabel = stringResource(R.string.share)
    LaunchedEffect(vm.sharedPhotos, state.busy, state.error, currentEntry, replacementPhotos, pendingPhotos, metadataPendingPhotos, metadataReplacement, metadataEdits.busy, closingFeature) {
        val incoming=vm.sharedPhotos
        if(incoming!=null && !state.busy && !metadataEdits.busy && closingFeature==null && state.error==null && currentEntry!=null && replacementPhotos==null && pendingPhotos==null && metadataPendingPhotos==null && metadataReplacement==null) {
            showExport=false;showPhotoMenu=false
            if(cardChanges || currentEntry?.destination?.route!=PhotoPage.EDITOR.name) replacementPhotos=incoming.map(Uri::toString)
            else importConfirmedPhotos(incoming)
            vm.consumeSharedPhotos()
        }
    }
    LaunchedEffect(state.notice?.id, state.error) {
        state.notice?.takeIf { state.error==null }?.let { notice ->
            snackbar.showSnackbar(ExportNoticeVisuals(notice,if(notice.photos.isNotEmpty())shareLabel else null))
            vm.clearNotice(notice.id)
        }
    }
    val currentPage = PhotoPage.entries.firstOrNull { it.name == currentEntry?.destination?.route }
    LaunchedEffect(currentPage) { dust.clear() }
    val sharesCards = state.settings.workspace.owner(ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.METADATA) == ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.CARDS
    val colorOwner = state.settings.workspace.owner(ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.COLORS)
    val colorPhoto = when (colorOwner) {
        ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.CARDS -> state.originalPhoto(vm.motionClip(activePhotoId))
        ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.METADATA -> metadataState.current
        ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.COLORS -> colorState.current
    }
    var colorHasHDR by remember { mutableStateOf(false) }
    var colorShowsHDR by remember { mutableStateOf(true) }
    val metadataPhoto = if (sharesCards) state.originalPhoto(vm.motionClip(activePhotoId)) else metadataState.current
    val metadataControls = remember(metadataPhoto?.id) { OriginalPreviewState() }
    val metadataHasHdr = metadataPhoto?.bitmap.hasHdrPreviewContent()
    val metadataHdrAvailable = metadataHasHdr && LocalView.current.display?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
    val inMetadata = currentPage == PhotoPage.METADATA
    val inEditor = currentPage == PhotoPage.EDITOR || currentPage == PhotoPage.PREVIEW
    val inColors = currentPage == PhotoPage.COLORS
    PreviewDynamicRange(if (inColors) colorHasHDR else if (inMetadata) metadataHasHdr else inEditor && hasHdrContent,
        if (inColors) colorHasHDR && colorShowsHDR else
        if (inMetadata) metadataHdrAvailable && metadataControls.hdr && metadataControls.mode != OriginalPreviewMode.DEPTH
        else inEditor && hdrEnabled && hdrAvailable)
    PhotoDissolveHost(dust) {
    CompositionLocalProvider(LocalNavigationBackdrop provides backdrop) {
    BoxWithConstraints {
    val useRail = maxWidth >= 840.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.4f
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!useRail && !WindowInsets.isImeVisible && currentPage in topLevelPages) PhotoBottomBar(
                currentPage ?: PhotoPage.EDITOR, !state.exporting && !state.closing && !metadataEdits.busy,
                backdrop, state.settings.workspace,
                ::selectTab)
        },
    ) { outerPadding ->
    CompositionLocalProvider(LocalBottomNavigationInset provides outerPadding.calculateBottomPadding()) {
    Row(Modifier.fillMaxSize().consumeWindowInsets(outerPadding)) {
    if (useRail && currentPage in topLevelPages) PhotoSideRail(currentPage ?: PhotoPage.EDITOR, !state.exporting && !state.closing && !metadataEdits.busy, ::selectTab)
    Box(Modifier.weight(1f).fillMaxHeight()) {
    NavHost(
        navController = navigation,
        startDestination = startupPage,
        enterTransition = {
            if (targetState.destination.route in setOf(PhotoPage.LENSES.name, PhotoPage.LENS_EDIT.name))
                navigationEnter(state.settings.workspace.predictiveBackStyle, motion)
            else EnterTransition.None
        },
        exitTransition = {
            if (initialState.destination.route in setOf(PhotoPage.LENSES.name, PhotoPage.LENS_EDIT.name))
                navigationExit(state.settings.workspace.predictiveBackStyle, motion)
            else ExitTransition.None
        },
        popEnterTransition = {
            if (targetState.destination.route in setOf(PhotoPage.LENSES.name, PhotoPage.LENS_EDIT.name))
                navigationEnter(state.settings.workspace.predictiveBackStyle, motion, pop = true)
            else EnterTransition.None
        },
        popExitTransition = {
            if (initialState.destination.route in setOf(PhotoPage.LENSES.name, PhotoPage.LENS_EDIT.name))
                navigationExit(state.settings.workspace.predictiveBackStyle, motion, pop = true)
            else ExitTransition.None
        },
    ) {
        composable(PhotoPage.MAP.name) {
            PhotoMapScreen(onOpenMetadata = { selectTab(PhotoPage.METADATA) })
        }
        composable(PhotoPage.EDITOR.name) {
            val rootBackEnabled = (state.photos.isNotEmpty() || state.importing) && !state.closing &&
                !showExport && !showPhotoMenu && replacementPhotos == null && state.error == null
            SystemBackObserver(enabled = rootBackEnabled && !cardChanges && !state.busy && !metadataEdits.busy) {
                vm.closeSession(showProgress = false) { }
            }
            rememberConfirmedBack(onConfirmed = { vm.closeSession(onClosed = onExit) },
                hasChanges = cardChanges || state.busy || metadataEdits.busy, handleCleanBack = Build.VERSION.SDK_INT < 36,
                enabled = rootBackEnabled,
                title = R.string.exit_title, message = R.string.exit_message, confirmLabel = R.string.exit_confirm)
            val editorActions: @Composable RowScope.()->Unit = {
                Box {
                    FuyaoAppBarAction(R.drawable.ic_photo_add,stringResource(R.string.select_photo),{ showPhotoMenu=true },enabled=!state.busy)
                    DropdownMenu(showPhotoMenu,onDismissRequest={ showPhotoMenu=false }) {
                        DropdownMenuItem(text={ Text(stringResource(R.string.from_gallery)) },onClick={
                            showPhotoMenu=false;photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        DropdownMenuItem(text={ Text(stringResource(R.string.from_file)) },onClick={ showPhotoMenu=false;filePicker.launch(arrayOf("image/*", ing.fuyaoskyrocket.photoinfo.domain.media.FuyaoPhotosPackage.MIME, "application/octet-stream")) })
                    }
                }
                val exportNotice=exportBlockingNotice(state)
                ing.fuyaoskyrocket.photoinfo.ui.components.SaveProgressAction(
                    label = if(state.photos.size>1) stringResource(R.string.batch_export,state.photos.size) else stringResource(R.string.export),
                    saving = state.exporting, completed = state.exportCompleted, total = state.exportTotal, enabled = !state.busy) {
                    if(exportNotice!=null)showExportBlocked=true else showExport=true
                }
                FuyaoIconButton(R.drawable.ic_close, stringResource(R.string.close_photo), { requestClose(PhotoFeature.CARDS) },
                    enabled = !state.exporting && !state.closing && !metadataEdits.busy)
            }
            FuyaoScaffold(title="",showTopBar=false,
                snackbarHost={ SnackbarHost(snackbar, Modifier.padding(bottom = LocalBottomNavigationInset.current)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))) { data ->
                ExportNotice(data,onOpen={ notice ->
                    vm.clearNotice(notice.id)
                    if(!PhotoIntents.launch(context,PhotoIntents.open(notice.photos.last()))) vm.reportExternalError(R.string.open_photo_failed,R.string.error_export_title)
                },onShare={ notice ->
                    vm.clearNotice(notice.id)
                    if(!PhotoIntents.launch(context,Intent.createChooser(PhotoIntents.share(notice.photos),shareLabel))) vm.reportExternalError(R.string.share_failed,R.string.error_export_title)
                })
            } }) { padding ->
                    if(state.photos.isEmpty()) {
                        FuyaoFormPage(padding) {
                            PhotoImportCard(stringResource(R.string.photo_cards_title),
                                stringResource(R.string.empty_hint,PhotoEditSnapshot.MAX_PHOTOS),R.drawable.ic_photo_add,state.busy,
                                onGallery={ photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onFiles={ filePicker.launch(arrayOf("image/*", ing.fuyaoskyrocket.photoinfo.domain.media.FuyaoPhotosPackage.MIME, "application/octet-stream")) })
                        }
                    } else {
                        PhotoWorkspaceCanvas(state.original ?: state.preview, padding) {
                        val photoId = state.photos.getOrNull(state.photoIndex)?.id
                        EditorWorkspace(
                            preview = { modifier, bottomSafe ->
                                EditorPreviewPane(state, vm::selectPhoto, original, { original = !original },
                                    { openPage(PhotoPage.EDITOR, PhotoPage.PREVIEW) }, vm.motionClip(photoId),state.hdrPhoto || hasHdrContent,
                                    hdrEnabled,hdrAvailable,{ hdrEnabled=!hdrEnabled },modifier,bottomSafe,editingPhotoText,editorActions)
                            },
                            controls = { modifier ->
                                key(photoId) {
                                    EditorControls(state, { field, value -> vm.updateField(field, value, photoId) },
                                        { vm.updateStyle(it, photoId) }, { field -> vm.resetField(field,photoId) },
                                        { vm.resetFields(photoId) },
                                        { fontPicker.launch(arrayOf("*/*")) }, vm::resetFont, vm::resolveLocation,
                                        { editingPhotoText = it }, modifier)
                                }
                            },
                        )
                    }
                        }
            }
        }
        composable(PhotoPage.METADATA.name) {
            PhotoMetadataScreen(photo = metadataPhoto, photos = if (sharesCards) state.photos else metadataState.photos,
                photoIndex = if (sharesCards) state.photoIndex else metadataState.photoIndex,
                busy = metadataEdits.busy || if (sharesCards) state.busy else metadataState.busy, sharesCards = sharesCards,
                controls = metadataControls, hdrAvailable = metadataHdrAvailable,
                onSelectPhoto = { if (sharesCards) vm.selectPhoto(it) else metadata.selectPhoto(it) },
                onGallery = { importFeature = ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.METADATA; metadataGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onFiles = { importFeature = ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.METADATA; metadataFiles.launch(arrayOf("image/*", ing.fuyaoskyrocket.photoinfo.domain.media.FuyaoPhotosPackage.MIME, "application/octet-stream")) },
                onClose = { requestClose(PhotoFeature.METADATA) }, edit = metadataEdits)
        }
        composable(PhotoPage.COLORS.name) {
            val ownerPhotos = if (colorOwner == ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.METADATA) metadataState else colorState
            val cardOwner = colorOwner == ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.CARDS
            ing.fuyaoskyrocket.photoinfo.features.colors.ColorsScreen(colorPhoto,
                if (cardOwner) state.photos else ownerPhotos.photos,
                if (cardOwner) state.photoIndex else ownerPhotos.photoIndex,
                if (cardOwner) state.busy else ownerPhotos.busy,
                onSelect = { if (cardOwner) vm.selectPhoto(it) else if (colorOwner == ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.METADATA) metadata.selectPhoto(it) else colors.selectPhoto(it) },
                onGallery = { importFeature = ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.COLORS; metadataGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onFiles = { importFeature = ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.COLORS; metadataFiles.launch(arrayOf("image/*", ing.fuyaoskyrocket.photoinfo.domain.media.FuyaoPhotosPackage.MIME, "application/octet-stream")) },
                onCamera = { importFeature = ing.fuyaoskyrocket.photoinfo.domain.model.PhotoFeature.COLORS; captureColorPhoto() },
                onHDR = { available, enabled -> colorHasHDR = available; colorShowsHDR = enabled },
                onClose = { requestClose(PhotoFeature.COLORS) })
        }
        composable(PhotoPage.SETTINGS.name) {
            SettingsScreen(state.settings.copy(lenses = settingsLenses), state.photos.isNotEmpty(),
                canSave = !state.busy,
                onManageLenses = { editedLens = null; openPage(PhotoPage.SETTINGS, PhotoPage.LENSES) },
                onBack = { returnFrom(PhotoPage.SETTINGS) },
                onDirtyChanged = { settingsDirty = it },
                onSave = { settings, apply ->
                    if (!vm.state.busy && navigation.currentBackStackEntry?.destination?.route == PhotoPage.SETTINGS.name) {
                        vm.saveSettings(settings)
                        if (apply) vm.applyDefaultAuthor()
                        settingsDirty = false
                    }
                })
        }
        composable(PhotoPage.LENSES.name) {
            LensProfilesScreen(settingsLenses, state.sourceModel, editedLens,
                onEditConsumed = { editedLens = null },
                onEdit = { if(atPage(PhotoPage.LENSES)) { editingLens = it.fields(); openPage(PhotoPage.LENSES, PhotoPage.LENS_EDIT) } },
                onBack = { returnFrom(PhotoPage.LENSES) },
                onSave = { profiles ->
                    if (!vm.state.busy && atPage(PhotoPage.LENSES)) {
                        vm.saveSettings(state.settings.copy(lenses = profiles))
                        settingsLenses = profiles
                        navigation.popBackStack()
                    }
                })
        }
        composable(PhotoPage.LENS_EDIT.name) {
            editingLens?.let { fields ->
                LensEditScreen(lensFromFields(fields), currentExifModel = state.sourceModel, onBack = { returnFrom(PhotoPage.LENS_EDIT) },
                    onSave = { if (atPage(PhotoPage.LENS_EDIT)) { editedLens = it.fields(); navigation.popBackStack() } })
            }
        }
        composable(PhotoPage.PREVIEW.name) {
            val bitmap = if (original) state.original else state.preview
            val photoId=state.photos.getOrNull(state.photoIndex)?.id
            if (bitmap != null && photoId != null) FullScreenPreview(bitmap,photoId,
                loadFullResolution={ vm.fullResolutionPreview(photoId,original) },motion=vm.motionClip(photoId),
                showHdr=state.hdrPhoto || hasHdrContent,hdrEnabled=hdrEnabled,hdrAvailable=hdrAvailable,onHdr={ hdrEnabled=!hdrEnabled }) { returnFrom(PhotoPage.PREVIEW) }
            else LaunchedEffect(state.busy) { if (!state.busy) returnFrom(PhotoPage.PREVIEW) }
        }
    }
    }
    }
    }
    }
    }
    }
    }
    closingFeature?.let { feature ->
        AlertDialog(onDismissRequest = { closingFeature = null }, title = { Text(stringResource(R.string.close_photo_title)) },
            text = { Text(stringResource(R.string.close_photo_message)) },
            confirmButton = { TextButton(onClick = { closingFeature = null; closePhotos(feature) }) { Text(stringResource(R.string.close_photo)) } },
            dismissButton = { TextButton(onClick = { closingFeature = null }) { Text(stringResource(R.string.continue_editing)) } })
    }
    pendingTab?.let { page ->
        AlertDialog(onDismissRequest = { pendingTab = null },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.settings_leave_message)) },
            confirmButton = { TextButton(onClick = {
                pendingTab = null
                settingsDirty = false
                navigateToTab(page)
            }) { Text(stringResource(R.string.discard_changes)) } },
            dismissButton = { TextButton(onClick = { pendingTab = null }) {
                Text(stringResource(R.string.continue_editing))
            } })
    }
    metadataReplacement?.let { selected ->
        AlertDialog(onDismissRequest = { metadataReplacement = null },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.metadata_shared_replace)) },
            confirmButton = { TextButton(onClick = {
                metadataReplacement = null
                confirmMetadataImport(selected.map(Uri::parse))
            }) { Text(stringResource(R.string.replace_photos_confirm)) } },
            dismissButton = { TextButton(onClick = { metadataReplacement = null }) { Text(stringResource(R.string.continue_editing)) } })
    }
    metadataState.error?.let { message ->
        AlertDialog(onDismissRequest = metadata::clearError,
            title = { Text(stringResource(R.string.error_import_title)) }, text = { Text(message) },
            confirmButton = { TextButton(onClick = metadata::clearError) { Text(stringResource(R.string.close)) } })
    }
    colorState.error?.let { message ->
        AlertDialog(onDismissRequest = colors::clearError,
            title = { Text(stringResource(R.string.error_import_title)) }, text = { Text(message) },
            confirmButton = { TextButton(onClick = colors::clearError) { Text(stringResource(R.string.close)) } })
    }
    replacementPhotos?.let { selected ->
        AlertDialog(onDismissRequest = { replacementPhotos = null },
            title = { Text(stringResource(if(state.photos.size==1)R.string.replace_photos_title_one else R.string.replace_photos_title_many)) },
            text = { Text(stringResource(if(selected.size==1)R.string.replace_photos_message_one else R.string.replace_photos_message_many,
                selected.size)) },
            confirmButton = { TextButton(onClick = {
                replacementPhotos = null
                importConfirmedPhotos(selected.map(Uri::parse))
            }) { Text(stringResource(R.string.replace_photos_confirm)) } },
            dismissButton = { TextButton(onClick = { replacementPhotos = null }) { Text(stringResource(R.string.continue_editing)) } })
    }
    if (showExportBlocked) AlertDialog(onDismissRequest={ showExportBlocked=false },
        title={ Text(stringResource(R.string.photo_export_blocked_title)) },
        text={ Column {
            Text(exportBlockingNotice(state) ?: stringResource(R.string.media_unsupported))
            state.blockDetail?.takeIf { it.isNotBlank() }?.let { detail ->
                Spacer(Modifier.height(8.dp))
                Text(detail, style = MaterialTheme.typography.labelSmall)
            }
        } },
        confirmButton={ TextButton(onClick={ showExportBlocked=false }) { Text(stringResource(R.string.close)) } })
    if (showExport) ExportDialog(
        width = state.width, height = state.height, count = state.photos.size, jpegRequired = state.exportRequiresJpeg,
        hasMotion = state.exportHasMotion, hasPortrait = state.exportHasPortrait, avifRequired=state.exportRequiresAvif, defaults = state.settings.exportDefaults,
        onDismiss = { showExport = false }, onExport = { options ->
            vm.setExportOptions(options)
            val format=options.format
            showExport = false
            if(options.separateLivePhoto && state.exportHasMotion) { folderFormat=format.name; exportFolder.launch(null) }
            else if (Build.VERSION.SDK_INT >= 29) vm.export(format)
            else if (state.photos.size > 1) { folderFormat = format.name; exportFolder.launch(null) }
            else if (format == ExportFormat.JPEG) jpegDestination.launch(PhotoExporter.filename(format, state.motionPhoto))
            else if(format == ExportFormat.HEIC) heicDestination.launch(PhotoExporter.filename(format))
            else pngDestination.launch(PhotoExporter.filename(format, state.motionPhoto))
        },
    )
    if (state.closing && !state.closingInBackground) AlertDialog(onDismissRequest = {}, confirmButton = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp)); Text(stringResource(R.string.closing_session))
        } })
    var showErrorDetails by rememberSaveable { mutableStateOf(false) }
    state.error?.let { message ->
        AlertDialog(onDismissRequest = vm::clearError, title = { Text(stringResource(state.errorTitle)) },
            text = { Text(message, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text(stringResource(R.string.close)) } },
            dismissButton = state.errorDetail?.let { detail ->
                {
                    Row {
                        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                        val copied = state.error.orEmpty() + "\n\n" + detail
                        TextButton(onClick = { clipboard.setText(AnnotatedString(copied)) }) {
                            Text(stringResource(R.string.error_copy_details))
                        }
                        TextButton(onClick = { showErrorDetails = true }) {
                            Text(stringResource(R.string.error_show_details))
                        }
                    }
                }
            })
    }
    if (showErrorDetails) {
        AlertDialog(onDismissRequest = { showErrorDetails = false },
            title = { Text(stringResource(state.errorTitle)) },
            text = {
                Text(state.errorDetail.orEmpty(),
                    Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall)
            },
            confirmButton = { TextButton(onClick = { showErrorDetails = false }) { Text(stringResource(R.string.close)) } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDialog(width: Int, height: Int, count: Int, jpegRequired: Boolean, hasMotion: Boolean, hasPortrait: Boolean, avifRequired: Boolean, defaults: ExportOptions,
    onDismiss: () -> Unit, onExport: (ExportOptions) -> Unit) {
    var options by rememberSaveable(stateSaver=ExportOptionsSaver) {
        val supported = if ((Build.VERSION.SDK_INT < 28 && defaults.format == ExportFormat.HEIC) ||
            (Build.VERSION.SDK_INT < 34 && defaults.format == ExportFormat.AVIF) ||
            (hasPortrait && !defaults.applePortrait && !defaults.appleStyle) ||
            (hasMotion && defaults.separateLivePhoto && defaults.format !in setOf(ExportFormat.JPEG,ExportFormat.HEIC)))
            defaults.copy(format = ExportFormat.JPEG, appleStyle = false, appleStyle3 = false) else defaults
        mutableStateOf((when {
            avifRequired && supported.format !in setOf(ExportFormat.HEIC,ExportFormat.AVIF) ->
                supported.copy(format = if (Build.VERSION.SDK_INT >= 28 &&
                    ing.fuyaoskyrocket.photoinfo.platform.ImageEncoderSupport.supports(ExportFormat.HEIC))
                    ExportFormat.HEIC else ExportFormat.AVIF)
            (hasPortrait && supported.applePortrait) || supported.appleStyle -> supported.copy(format=ExportFormat.HEIC,
                applePortrait=supported.applePortrait || (supported.appleStyle && hasPortrait))
            else -> supported
        }).photoSave().sanitized(jpegRequired))
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FuyaoSpacing.content),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.save_options), style = MaterialTheme.typography.headlineSmall)
            Text(if(count>1) stringResource(R.string.batch_export_size,count) else stringResource(R.string.export_size, width, height),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExportOptionsControls(options,{ options=it },jpegRequired,hasMotion,hasPortrait,showLiveOption=hasMotion,showPortraitOption=hasPortrait,avifRequired=avifRequired)
            Text(stringResource(R.string.export_temporary_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(if (options.separateLivePhoto && hasMotion) R.string.package_destination_hint else R.string.gallery_destination_hint),
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(avifRequired)Text(stringResource(R.string.avif_precision_required),style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            val compatible=(!hasPortrait || options.format==(if(options.applePortrait)ExportFormat.HEIC else ExportFormat.JPEG)) &&
                (!hasMotion || options.format==ExportFormat.JPEG || options.format==ExportFormat.HEIC) &&
                (!avifRequired || options.format==ExportFormat.AVIF || options.format==ExportFormat.HEIC) &&
                (!options.appleStyle || options.format==ExportFormat.HEIC) &&
                (!options.appleStyle3 || options.format==ExportFormat.HEIC) && !(hasMotion && options.separateLivePhoto && options.appleStyle)
            if(!compatible)Text(stringResource(R.string.export_formats_conflict),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            Button(enabled = !submitting && compatible && ing.fuyaoskyrocket.photoinfo.platform.ImageEncoderSupport.supports(options.format), onClick = {
                submitting = true
                scope.launch {
                    try {
                        sheetState.hide()
                        onExport(options.sanitized(jpegRequired))
                    } finally { submitting = false }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (options.separateLivePhoto && hasMotion) stringResource(R.string.package_export_action)
                    else if(count>1)stringResource(R.string.batch_export,count) else stringResource(R.string.export))
            }
            TextButton(enabled = !submitting, onClick = {
                scope.launch { sheetState.hide(); onDismiss() }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}
