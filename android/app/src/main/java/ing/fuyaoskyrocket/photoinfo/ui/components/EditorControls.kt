package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.model.CardStyle
import ing.fuyaoskyrocket.photoinfo.domain.model.FieldId
import ing.fuyaoskyrocket.photoinfo.presentation.EditorState
import ing.fuyaoskyrocket.photoinfo.presentation.LocationStatus
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoSpacing
import ing.fuyaoskyrocket.photoinfo.ui.theme.LocalPhotoMotionEnabled
import kotlin.math.roundToInt

@Composable
fun EditorControls(
    state: EditorState,
    onField: (FieldId, String) -> Unit,
    onStyle: (CardStyle) -> Unit,
    onResetField: (FieldId) -> Unit,
    onResetAllFields: () -> Unit,
    onImportFont: () -> Unit,
    onResetFont: () -> Unit,
    onResolveLocation: () -> Unit,
    onEditingActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var fieldIndex by rememberSaveable { mutableIntStateOf(0) }
    var styleIndex by rememberSaveable { mutableIntStateOf(0) }
    var editingActive by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    var imeSeen by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val transitionMillis = if (LocalPhotoMotionEnabled.current) 180 else 0
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val editingCallback = rememberUpdatedState(onEditingActiveChange)
    fun setEditingActive(active: Boolean) {
        if (editingActive != active) {
            editingActive = active
            editingCallback.value(active)
        }
    }
    LaunchedEffect(imeVisible) {
        if (imeVisible) imeSeen = true
        else if (imeSeen) {
            imeSeen = false
            setEditingActive(false)
        }
    }
    DisposableEffect(Unit) { onDispose { editingCallback.value(false) } }
    val fieldLabels = FieldId.entries.map { stringResource(if (it == FieldId.FOCAL_LENGTH) R.string.wheel_focal else fieldLabel(it)) }
    val styleLabels = StyleSetting.entries.map { stringResource(it.label) } + stringResource(R.string.font)
    val currentState = rememberUpdatedState(state)
    val currentOnField = rememberUpdatedState(onField)
    val currentResolve = rememberUpdatedState(onResolveLocation)
    // Keep the native input's selection and composition when the keyboard changes the layout.
    val fieldContent = remember {
        movableContentOf {
            FieldControl(currentState.value, FieldId.entries[fieldIndex], currentOnField.value, currentResolve.value,
                { fieldFocused = it; if (!it) setEditingActive(false) },
                { if (fieldFocused) setEditingActive(true) })
        }
    }
    BoxWithConstraints(modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))) {
    if (maxHeight < 240.dp || maxWidth < 320.dp || LocalDensity.current.fontScale > 1.6f) {
        CompactEditorControl(state, tab, fieldIndex, styleIndex, { nextTab, nextIndex ->
            focus.clearFocus(); setEditingActive(false); tab = nextTab
            if (nextTab == 0) fieldIndex = nextIndex else styleIndex = nextIndex
        }, fieldContent, onStyle, onResetField, onResetAllFields, onImportFont, onResetFont, Modifier.fillMaxSize())
    } else Column(Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally) {
        PrimaryTabRow(selectedTabIndex = tab, containerColor = androidx.compose.ui.graphics.Color.Transparent, divider = {}) {
            listOf(R.string.tab_info, R.string.tab_style).forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { focus.clearFocus(); setEditingActive(false); tab = index },
                    text = { Text(stringResource(title)) })
            }
        }
        Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().weight(1f).padding(horizontal = FuyaoSpacing.content),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Crossfade(targetState = tab, animationSpec = tween(transitionMillis), label = "editor wheel",
                modifier = Modifier.weight(.34f).fillMaxHeight()) { pickerTab ->
                val pickerLabels = if (pickerTab == 0) fieldLabels else styleLabels
                CyclicItemSelector(pickerLabels, if (pickerTab == 0) fieldIndex else styleIndex, !state.busy && pickerTab == tab,
                    Modifier.fillMaxSize().semantics { if (pickerTab != tab) hideFromAccessibility() }) { index ->
                    if (tab == pickerTab && index in pickerLabels.indices) {
                        focus.clearFocus(); setEditingActive(false)
                        if (pickerTab == 0) fieldIndex = index else styleIndex = index
                    }
                }
            }
            EditorInspector(state, tab, fieldIndex, styleIndex, fieldContent, onStyle, onResetField,
                onResetAllFields, onImportFont, onResetFont, editingActive,
                Modifier.weight(.66f).fillMaxHeight())
        }
    }
    }
}

@Composable
private fun CompactEditorControl(state: EditorState, tab: Int, fieldIndex: Int, styleIndex: Int,
    onChoice: (Int, Int) -> Unit, fieldContent: @Composable () -> Unit, onStyle: (CardStyle) -> Unit,
    onResetField: (FieldId) -> Unit, onResetAll: () -> Unit, onImportFont: () -> Unit, onResetFont: () -> Unit,
    modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    val field = FieldId.entries[fieldIndex]
    val setting = StyleSetting.entries.getOrNull(styleIndex)
    Row(modifier.padding(horizontal = FuyaoSpacing.content), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(.34f)) {
            OutlinedButton(onClick = { menu = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (tab == 0) fieldLabel(field) else setting?.label ?: R.string.font),
                    style = MaterialTheme.typography.labelMedium)
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                Text(stringResource(R.string.tab_info), Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                FieldId.entries.forEachIndexed { index, item ->
                    DropdownMenuItem(text = { Text(stringResource(fieldLabel(item))) },
                        onClick = { menu = false; onChoice(0, index) })
                }
                HorizontalDivider()
                Text(stringResource(R.string.tab_style), Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                StyleSetting.entries.forEachIndexed { index, item ->
                    DropdownMenuItem(text = { Text(stringResource(item.label)) },
                        onClick = { menu = false; onChoice(1, index) })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.font)) },
                    onClick = { menu = false; onChoice(1, StyleSetting.entries.size) })
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.restore_current)) }, onClick = {
                    menu = false
                    if (tab == 0) onResetField(field)
                    else if (setting == null) onResetFont()
                    else onStyle(setting.update(state.style, setting.value(CardStyle())))
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.restore_all)) }, onClick = {
                    menu = false
                    if (tab == 0) onResetAll() else { onResetFont(); onStyle(CardStyle()) }
                })
            }
        }
        Box(Modifier.weight(.66f).heightIn(max = 88.dp)) {
            if (tab == 0) fieldContent()
            else if (setting == null) FontControl(state, onImportFont)
            else CardStyleSlider(setting.value(state.style), { onStyle(setting.update(state.style, it)) },
                setting.minimum..setting.maximum, setting.value(CardStyle()), stringResource(setting.label),
                styleValue(setting.percentage, setting.value(state.style)),
                styleValue(setting.percentage, setting.maximum), !state.busy, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EditorInspector(
    state: EditorState,
    tab: Int,
    fieldIndex: Int,
    styleIndex: Int,
    fieldContent: @Composable () -> Unit,
    onStyle: (CardStyle) -> Unit,
    onResetField: (FieldId) -> Unit,
    onResetAllFields: () -> Unit,
    onImportFont: () -> Unit,
    onResetFont: () -> Unit,
    editingActive: Boolean,
    modifier: Modifier,
) {
    val transitionMillis = if (LocalPhotoMotionEnabled.current) 180 else 0
    BoxWithConstraints(modifier) {
        val tight = maxHeight < 200.dp
        val controlHeight = if (tight) 56.dp else 72.dp
        val hintHeight = 32.dp
        val showHint = maxHeight >= controlHeight + 48.dp + hintHeight + 96.dp && LocalDensity.current.fontScale <= 1.4f
        val previewRoom = maxHeight - controlHeight - 48.dp -
            (if (showHint) hintHeight + 16.dp else 8.dp)
        val previewHeight = minOf(maxWidth, previewRoom.coerceAtLeast(0.dp))
        val showPreview = previewHeight >= 48.dp && LocalDensity.current.fontScale < 1.5f
        val currentField = FieldId.entries[fieldIndex]
        val currentStyle = StyleSetting.entries.getOrNull(styleIndex)
        val hint = when {
            tab == 0 -> fieldHint(currentField)
            currentStyle != null -> currentStyle.hint
            else -> R.string.font_hint
        }
        val resetCurrentLabel = stringResource(R.string.restore_current)
        val resetAllLabel = stringResource(R.string.restore_all)
        val shortLabels = maxWidth < 230.dp || tight
        val canResetItem = !state.busy && (tab != 1 || currentStyle != null || state.hasCustomFont)
        val resetItem: () -> Unit = {
            if (tab == 0) onResetField(currentField)
            else if (currentStyle == null) onResetFont()
            else onStyle(currentStyle.update(state.style, currentStyle.value(CardStyle())))
        }
        val resetAll: () -> Unit = {
            if (tab == 0) onResetAllFields()
            else {
                if (state.hasCustomFont) onResetFont()
                onStyle(CardStyle())
            }
        }
        PhotoAmbientBackdrop(state.original,
            modifier = Modifier.align(Alignment.TopCenter).requiredWidth(maxWidth + 24.dp)
                .height(minOf(240.dp, maxHeight * .58f)))
        Column(Modifier.fillMaxWidth()) {
            if (showPreview) {
                CardDetailPreview(
                    bitmap = state.preview,
                    box = state.previewCardBox,
                    rendering = state.rendering || state.loadingPhoto,
                    errorMessage = state.previewError,
                    editingActive = editingActive,
                    highlightRects = if (tab == 0) state.previewFieldRects[currentField].orEmpty() else emptyList(),
                    highlightStyle = when {
                        tab == 0 && state.previewFieldRects[currentField].isNullOrEmpty() -> CardPreviewStyleHighlight.CARD
                        tab == 0 || currentStyle == null -> null
                        currentStyle == StyleSetting.RIGHT -> CardPreviewStyleHighlight.RIGHT
                        currentStyle == StyleSetting.BOTTOM -> CardPreviewStyleHighlight.BOTTOM
                        else -> CardPreviewStyleHighlight.CARD
                    },
                    modifier = Modifier.fillMaxWidth().height(previewHeight),
                )
                Spacer(Modifier.height(8.dp))
            }
            Box(Modifier.fillMaxWidth().height(controlHeight)) {
                if (tab == 0) fieldContent()
                else Crossfade(targetState = styleIndex, animationSpec = tween(transitionMillis), label = "editor style") { selectedIndex ->
                val current = selectedIndex == styleIndex && tab == 1
                Box(Modifier.fillMaxSize().semantics { if (!current) hideFromAccessibility() }) {
                if (selectedIndex == StyleSetting.entries.size) {
                    FontControl(state, { if (current) onImportFont() }, enabled = !state.busy && current)
                } else {
                    val setting = StyleSetting.entries[selectedIndex]
                    val value = setting.value(state.style)
                    CardStyleSlider(value, { if (current) onStyle(setting.update(state.style, it)) },
                        setting.minimum..setting.maximum, setting.value(CardStyle()), stringResource(setting.label),
                        styleValue(setting.percentage, value),
                        styleValue(setting.percentage, setting.maximum), !state.busy && current, Modifier.fillMaxWidth())
                }
                }
                }
            }
            if (showHint) {
                Spacer(Modifier.height(8.dp))
                Crossfade(targetState = hint, animationSpec = tween(transitionMillis), label = "editor hint",
                    modifier = Modifier.fillMaxWidth().height(hintHeight)) { shownHint ->
                    val hintText = stringResource(shownHint)
                    Text(hintText, Modifier.semantics { if (shownHint != hint) hideFromAccessibility() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = resetItem, enabled = canResetItem,
                    modifier = Modifier.weight(1f).height(48.dp).semantics { contentDescription = resetCurrentLabel }) {
                    Text(stringResource(if (shortLabels) R.string.restore_current_short else R.string.restore_current),
                        style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                TextButton(onClick = resetAll, enabled = !state.busy,
                    modifier = Modifier.weight(1f).height(48.dp).semantics { contentDescription = resetAllLabel }) {
                    Text(stringResource(if (shortLabels) R.string.restore_all_short else R.string.restore_all),
                        style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun FieldControl(state: EditorState, field: FieldId, onField: (FieldId, String) -> Unit,
    onResolveLocation: () -> Unit, onFieldFocus: (Boolean) -> Unit, onFieldEdited: () -> Unit) {
    val canResolve = field == FieldId.LOCATION && state.hasPhotoGps && state.settings.resolvePhotoLocation
    val label = stringResource(fieldLabel(field))
    val retryLabel = stringResource(R.string.location_retry)
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(value = state.info[field], onValueChange = { onFieldEdited(); onField(field, it) },
            modifier = Modifier.fillMaxWidth().weight(1f)
                .onFocusChanged { onFieldFocus(it.isFocused) }
                .semantics { contentDescription = label },
            enabled = !state.busy, minLines = 1, maxLines = 3,
            placeholder = { Text(stringResource(R.string.field_value_placeholder)) },
            trailingIcon = if (canResolve) { {
                if (state.locationStatus == LocationStatus.RESOLVING) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else TextButton(onClick = onResolveLocation, enabled = !state.busy,
                    modifier = Modifier.semantics { contentDescription = retryLabel }) {
                    Text("GPS", style = MaterialTheme.typography.labelSmall)
                }
            } } else null,
            keyboardOptions = KeyboardOptions(keyboardType = when (field) {
                FieldId.ISO -> KeyboardType.Number
                FieldId.APERTURE -> KeyboardType.Decimal
                else -> KeyboardType.Text
            }),
        )
        if (canResolve && state.locationStatus == LocationStatus.RESOLVING) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FontControl(state: EditorState, onImportFont: () -> Unit, enabled: Boolean = !state.busy) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(state.fontName ?: stringResource(R.string.system_mono),
            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        OutlinedButton(onClick = onImportFont, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.import_font), maxLines = 1)
        }
    }
}

@Composable
private fun styleValue(percentage: Boolean, value: Float): String =
    stringResource(if (percentage) R.string.value_percent else R.string.value_pixels,
        (value * if (percentage) 100 else 1).roundToInt())

private enum class StyleSetting(val label: Int, val hint: Int, val minimum: Float,
    val maximum: Float, val percentage: Boolean) {
    CARD(R.string.card_scale, R.string.card_scale_hint, .6f, 2f, true),
    TEXT(R.string.text_scale, R.string.text_scale_hint, .8f, 1.8f, true),
    OPACITY(R.string.opacity, R.string.opacity_hint, 0f, 1f, true),
    BLUR(R.string.blur, R.string.blur_hint, 0f, 50f, false),
    RIGHT(R.string.right_inset, R.string.inset_hint, 0f, 250f, false),
    BOTTOM(R.string.bottom_inset, R.string.inset_hint, 0f, 250f, false),
    RADIUS(R.string.radius, R.string.radius_hint, 0f, 40f, false);

    fun value(style: CardStyle): Float = when (this) {
        CARD -> style.scale; TEXT -> style.textScale; OPACITY -> style.opacity; BLUR -> style.blur
        RIGHT -> style.rightInset; BOTTOM -> style.bottomInset; RADIUS -> style.cornerRadius
    }

    fun update(style: CardStyle, value: Float): CardStyle = when (this) {
        CARD -> style.copy(scale = value); TEXT -> style.copy(textScale = value)
        OPACITY -> style.copy(opacity = value); BLUR -> style.copy(blur = value)
        RIGHT -> style.copy(rightInset = value); BOTTOM -> style.copy(bottomInset = value)
        RADIUS -> style.copy(cornerRadius = value)
    }
}

private fun fieldLabel(field: FieldId) = when (field) {
    FieldId.DEVICE -> R.string.field_device; FieldId.AUTHOR -> R.string.field_author
    FieldId.LOCATION -> R.string.field_location; FieldId.CAMERA -> R.string.field_camera
    FieldId.IMAGE_SIZE -> R.string.field_size; FieldId.FOCAL_LENGTH -> R.string.field_focal
    FieldId.EXPOSURE -> R.string.field_exposure; FieldId.APERTURE -> R.string.field_aperture
    FieldId.ISO -> R.string.field_iso
    FieldId.PHOTOGRAPHIC_STYLE -> R.string.field_photographic_style
}

private fun fieldHint(field: FieldId) = when (field) {
    FieldId.DEVICE -> R.string.card_device_hint; FieldId.AUTHOR -> R.string.card_author_hint
    FieldId.LOCATION -> R.string.location_hint; FieldId.CAMERA -> R.string.card_lens_hint
    FieldId.IMAGE_SIZE -> R.string.card_pixels_hint; FieldId.FOCAL_LENGTH -> R.string.card_focal_hint
    FieldId.EXPOSURE -> R.string.card_exposure_hint; FieldId.APERTURE -> R.string.card_aperture_hint
    FieldId.ISO -> R.string.card_iso_hint
    FieldId.PHOTOGRAPHIC_STYLE -> R.string.card_photographic_style_hint
}
