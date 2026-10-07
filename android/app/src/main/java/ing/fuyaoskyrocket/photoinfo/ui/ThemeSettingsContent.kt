package ing.fuyaoskyrocket.photoinfo.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.model.*
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoFormSection
import ing.fuyaoskyrocket.photoinfo.ui.theme.photoColorScheme

internal val WorkspaceThemeSaver = listSaver<WorkspaceSettings, Any>(
    save = { listOf(it.appearance.name, it.glassNavigation, it.blurNavigation, it.dynamicTheme, it.themeSeed,
        it.themePalette.name, it.themeContrast.name, it.themeColorSpec.name, it.pureBlackTheme, it.predictiveBackStyle.name) },
    restore = { WorkspaceSettings(appearance = AppAppearance.valueOf(it[0] as String), glassNavigation = it[1] as Boolean,
        blurNavigation = it[2] as Boolean, dynamicTheme = it[3] as Boolean, themeSeed = it[4] as Long,
        themePalette = ThemePalette.valueOf(it[5] as String), themeContrast = ThemeContrast.valueOf(it[6] as String),
        themeColorSpec = ThemeColorSpec.valueOf(it[7] as String), pureBlackTheme = it[8] as Boolean,
        predictiveBackStyle = PredictiveBackStyle.valueOf(it[9] as String)) },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeSettingsContent(settings: WorkspaceSettings, onChange: (WorkspaceSettings) -> Unit) {
    val dark = settings.appearance.isDark(isSystemInDarkTheme())
    val seed = if (settings.dynamicTheme && Build.VERSION.SDK_INT >= 31) colorResource(android.R.color.system_accent1_500).toArgb()
        else settings.themeSeed.toInt()
    val preview = remember(seed, dark, settings) {
        photoColorScheme(seed, dark, settings.themePalette, settings.themeContrast, settings.themeColorSpec, settings.pureBlackTheme)
    }
    FuyaoFormSection(stringResource(R.string.theme_material3)) {
        Surface(color = preview.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.theme_preview), color = preview.onSurface, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(preview.primary to preview.onPrimary, preview.secondary to preview.onSecondary, preview.tertiary to preview.onTertiary)
                        .forEachIndexed { index, colors ->
                            Surface(color = colors.first, contentColor = colors.second, shape = MaterialTheme.shapes.medium) {
                                Text(stringResource(listOf(R.string.theme_primary, R.string.theme_secondary, R.string.theme_tertiary)[index]),
                                    Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                }
            }
        }
        ThemeChoice(stringResource(R.string.appearance), settings.appearance, AppAppearance.entries.associateWith {
            stringResource(when (it) { AppAppearance.SYSTEM -> R.string.appearance_system; AppAppearance.LIGHT -> R.string.appearance_light; AppAppearance.DARK -> R.string.appearance_dark })
        }) { onChange(settings.copy(appearance = it)) }
        ThemeSwitch(stringResource(R.string.theme_dynamic), stringResource(R.string.theme_dynamic_hint), settings.dynamicTheme && Build.VERSION.SDK_INT >= 31,
            enabled = Build.VERSION.SDK_INT >= 31) { onChange(settings.copy(dynamicTheme = it)) }
        if (!settings.dynamicTheme || Build.VERSION.SDK_INT < 31) ThemeSeedPicker(settings.themeSeed) { onChange(settings.copy(themeSeed = it)) }
        ThemeChoice(stringResource(R.string.theme_palette), settings.themePalette, ThemePalette.entries.associateWith {
            stringResource(when (it) {
                ThemePalette.TONAL_SPOT -> R.string.theme_palette_tonal; ThemePalette.NEUTRAL -> R.string.theme_palette_neutral
                ThemePalette.VIBRANT -> R.string.theme_palette_vibrant; ThemePalette.EXPRESSIVE -> R.string.theme_palette_expressive
                ThemePalette.RAINBOW -> R.string.theme_palette_rainbow; ThemePalette.FRUIT_SALAD -> R.string.theme_palette_fruit
                ThemePalette.MONOCHROME -> R.string.theme_palette_mono; ThemePalette.FIDELITY -> R.string.theme_palette_fidelity
                ThemePalette.CONTENT -> R.string.theme_palette_content
            })
        }) { onChange(settings.copy(themePalette = it)) }
        ThemeChoice(stringResource(R.string.theme_color_spec), settings.themeColorSpec, ThemeColorSpec.entries.associateWith {
            stringResource(if (it == ThemeColorSpec.SPEC_2025) R.string.theme_spec_2025 else R.string.theme_spec_2021)
        }) { onChange(settings.copy(themeColorSpec = it)) }
        Text(stringResource(R.string.theme_spec_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ThemeChoice(stringResource(R.string.theme_contrast), settings.themeContrast, ThemeContrast.entries.associateWith {
            stringResource(when (it) { ThemeContrast.STANDARD -> R.string.theme_contrast_standard; ThemeContrast.MEDIUM -> R.string.theme_contrast_medium; ThemeContrast.HIGH -> R.string.theme_contrast_high })
        }) { onChange(settings.copy(themeContrast = it)) }
        ThemeSwitch(stringResource(R.string.theme_black), stringResource(R.string.theme_black_hint), settings.pureBlackTheme) {
            onChange(settings.copy(pureBlackTheme = it))
        }
    }
    FuyaoFormSection(stringResource(R.string.theme_navigation)) {
        ThemeSwitch(stringResource(R.string.glass_navigation), stringResource(R.string.theme_glass_hint), settings.glassNavigation && Build.VERSION.SDK_INT >= 33,
            enabled = Build.VERSION.SDK_INT >= 33) { onChange(settings.copy(glassNavigation = it)) }
        ThemeSwitch(stringResource(R.string.theme_navigation_blur), stringResource(R.string.theme_navigation_blur_hint), settings.blurNavigation && Build.VERSION.SDK_INT >= 31,
            enabled = Build.VERSION.SDK_INT >= 31) { onChange(settings.copy(blurNavigation = it)) }
        if (Build.VERSION.SDK_INT < 33) Text(stringResource(R.string.theme_effect_support), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    FuyaoFormSection(stringResource(R.string.theme_back)) {
        ThemeChoice(stringResource(R.string.theme_back_style), settings.predictiveBackStyle, PredictiveBackStyle.entries.associateWith {
            stringResource(when (it) { PredictiveBackStyle.SYSTEM -> R.string.theme_back_system; PredictiveBackStyle.SLIDE -> R.string.theme_back_slide
                PredictiveBackStyle.SCALE -> R.string.theme_back_scale; PredictiveBackStyle.NONE -> R.string.theme_back_none })
        }) { onChange(settings.copy(predictiveBackStyle = it)) }
        Text(stringResource(R.string.theme_back_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ThemeSwitch(title: String, hint: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun <T> ThemeChoice(title: String, selected: T, options: Map<T, String>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Box {
            TextButton({ expanded = true }) { Text(options[selected].orEmpty()) }
            DropdownMenu(expanded, { expanded = false }) {
                options.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) },
                    trailingIcon = { if (selected == value) Icon(painterResource(R.drawable.ic_check), null) },
                    onClick = { onSelect(value); expanded = false }) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSeedPicker(seed: Long, onSelect: (Long) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable(seed) { mutableStateOf("%06X".format(seed and 0xFFFFFF)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.theme_seed), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0xFF6750A4, 0xFF006F6B, 0xFF0061A4, 0xFF895000, 0xFF984061, 0xFF506600).forEach { value ->
                FilterChip(selected = seed == value, onClick = { onSelect(value) }, label = { Text("#%06X".format(value and 0xFFFFFF)) },
                    leadingIcon = { Box(Modifier.size(18.dp).background(Color(value), MaterialTheme.shapes.small)) })
            }
        }
        TextButton({ input = "%06X".format(seed and 0xFFFFFF); editing = true }) { Text(stringResource(R.string.theme_seed_custom)) }
    }
    if (editing) {
        val valid = input.matches(Regex("[0-9a-fA-F]{6}"))
        AlertDialog(onDismissRequest = { editing = false }, title = { Text(stringResource(R.string.theme_seed_custom)) },
            text = { OutlinedTextField(input, { if (it.length <= 6) input = it }, singleLine = true,
                label = { Text(stringResource(R.string.theme_seed_format)) }, prefix = { Text("#") }, isError = !valid,
                supportingText = { if (!valid) Text(stringResource(R.string.theme_seed_invalid)) }) },
            confirmButton = { TextButton({ onSelect(0xFF000000 or input.toLong(16)); editing = false }, enabled = valid) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton({ editing = false }) { Text(stringResource(android.R.string.cancel)) } })
    }
}
