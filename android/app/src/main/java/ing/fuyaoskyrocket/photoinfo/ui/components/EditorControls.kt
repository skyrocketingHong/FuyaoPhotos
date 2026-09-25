package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.model.CardStyle
import ing.fuyaoskyrocket.photoinfo.domain.model.FieldId
import ing.fuyaoskyrocket.photoinfo.presentation.EditorState
import ing.fuyaoskyrocket.photoinfo.presentation.LocationStatus
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
    Column(modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        horizontalAlignment = Alignment.CenterHorizontally) {
        PrimaryTabRow(selectedTabIndex = tab, divider = {}) {
            listOf(R.string.tab_info, R.string.tab_style).forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { focus.clearFocus(); setEditingActive(false); tab = index },
                    text = { Text(stringResource(title)) })
            }
        }
        Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Crossfade(targetState = tab, animationSpec = tween(180), label = "editor wheel",
                modifier = Modifier.weight(.4f).fillMaxHeight()) { pickerTab ->
                val pickerLabels = if (pickerTab == 0) fieldLabels else styleLabels
                CyclicItemSelector(pickerLabels, if (pickerTab == 0) fieldIndex else styleIndex, !state.busy,
                    Modifier.fillMaxSize()) { index ->
                    if (tab == pickerTab && index in pickerLabels.indices) {
                        focus.clearFocus(); setEditingActive(false)
                        if (pickerTab == 0) fieldIndex = index else styleIndex = index
                    }
                }
            }
            EditorInspector(state, tab, fieldIndex, styleIndex, onField, onStyle, onResetField,
                onResetAllFields, onImportFont, onResetFont, onResolveLocation,
                editingActive, { fieldFocused = it; if (!it) setEditingActive(false) },
                { if (fieldFocused) setEditingActive(true) },
                Modifier.weight(.6f).fillMaxHeight())
        }
    }
}

@Composable
private fun EditorInspector(
    state: EditorState,
    tab: Int,
    fieldIndex: Int,
    styleIndex: Int,
    onField: (FieldId, String) -> Unit,
    onStyle: (CardStyle) -> Unit,
    onResetField: (FieldId) -> Unit,
    onResetAllFields: () -> Unit,
    onImportFont: () -> Unit,
    onResetFont: () -> Unit,
    onResolveLocation: () -> Unit,
    editingActive: Boolean,
    onFieldFocus: (Boolean) -> Unit,
    onFieldEdited: () -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val tight = maxHeight < 200.dp
        val scrollable = maxHeight < 380.dp
        val controlHeight = if (tight) 64.dp else 76.dp
        val hintHeight = if (maxWidth < 190.dp) 88.dp else 72.dp
        val showHint = maxHeight >= controlHeight + 48.dp + hintHeight + 16.dp || scrollable
        val previewRoom = maxHeight - controlHeight - 48.dp -
            (if (showHint) hintHeight + 20.dp else 12.dp)
        // The crop keeps the reference card ratio, so a full-width box has a stable height.
        val previewHeight = minOf(maxWidth / CardPreviewReference.aspect, previewRoom, 220.dp)
        val showPreview = maxHeight >= 210.dp && previewHeight >= 56.dp &&
            LocalDensity.current.fontScale < 1.5f
        val selection = tab to if (tab == 0) fieldIndex else styleIndex
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
        EditorAmbientBackdrop(state.original,
            modifier = Modifier.align(Alignment.TopCenter).requiredWidth(maxWidth + 24.dp)
                .height(minOf(240.dp, maxHeight * .58f)))
        Column(Modifier.fillMaxSize().then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
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
            Crossfade(targetState = selection, animationSpec = tween(180), label = "editor setting",
                modifier = Modifier.fillMaxWidth().height(controlHeight)) { (selectedTab, selectedIndex) ->
                if (selectedTab == 0) {
                    FieldControl(state, FieldId.entries[selectedIndex], onField, onResolveLocation,
                        onFieldFocus, onFieldEdited)
                } else if (selectedIndex == StyleSetting.entries.size) {
                    FontControl(state, onImportFont)
                } else {
                    val setting = StyleSetting.entries[selectedIndex]
                    val value = setting.value(state.style)
                    CardStyleSlider(value, { onStyle(setting.update(state.style, it)) },
                        setting.minimum..setting.maximum, setting.value(CardStyle()), stringResource(setting.label),
                        styleValue(setting.percentage, value), !state.busy, Modifier.fillMaxWidth())
                }
            }
            if (!scrollable) Spacer(Modifier.weight(1f))
            if (showHint) {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().heightIn(min = hintHeight), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val hintText = stringResource(hint)
                    Text(hintText, Modifier.semantics { contentDescription = hintText },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                    if (tab == 1 && currentStyle != null) {
                        Text(stringResource(R.string.style_reference, styleValue(currentStyle.percentage,
                            currentStyle.value(CardStyle()))),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
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

/** Reference card box ratio: the detail crop always keeps it, so the preview never jumps. */
internal object CardPreviewReference {
    val aspect = 215f / 168f
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
private fun FontControl(state: EditorState, onImportFont: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(state.fontName ?: stringResource(R.string.system_mono),
            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        OutlinedButton(onClick = onImportFont, enabled = !state.busy,
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
}

private fun fieldHint(field: FieldId) = when (field) {
    FieldId.DEVICE -> R.string.card_device_hint; FieldId.AUTHOR -> R.string.card_author_hint
    FieldId.LOCATION -> R.string.location_hint; FieldId.CAMERA -> R.string.card_lens_hint
    FieldId.IMAGE_SIZE -> R.string.card_pixels_hint; FieldId.FOCAL_LENGTH -> R.string.card_focal_hint
    FieldId.EXPOSURE -> R.string.card_exposure_hint; FieldId.APERTURE -> R.string.card_aperture_hint
    FieldId.ISO -> R.string.card_iso_hint
}
