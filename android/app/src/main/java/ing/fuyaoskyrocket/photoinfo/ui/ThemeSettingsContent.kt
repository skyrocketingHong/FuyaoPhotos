package ing.fuyaoskyrocket.photoinfo.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.model.*
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.FuyaoSpacing
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.SectionHeading
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.SettingsChoiceRow
import ing.fuyaoskyrocket.photoinfo.ui.theme.photoColorScheme

@Composable
internal fun ThemeSettingsContent(
    settings: WorkspaceSettings,
    enabled: Boolean = true,
    onChange: ((WorkspaceSettings) -> WorkspaceSettings) -> Unit,
) {
    val dark = settings.appearance.isDark(isSystemInDarkTheme())
    val seed = if (settings.dynamicTheme && Build.VERSION.SDK_INT >= 31) colorResource(android.R.color.system_accent1_500).toArgb()
        else settings.themeSeed.toInt()
    val preview = remember(seed, dark, settings) {
        photoColorScheme(seed, dark, settings.themePalette, settings.themeContrast, settings.themeColorSpec, settings.pureBlackTheme)
    }
    ThemePreview(preview)
    ThemeSettingsSection(stringResource(R.string.theme_material3)) {
        SettingsChoiceRow(stringResource(R.string.appearance), settings.appearance, AppAppearance.entries.associateWith {
            stringResource(when (it) { AppAppearance.SYSTEM -> R.string.appearance_system; AppAppearance.LIGHT -> R.string.appearance_light; AppAppearance.DARK -> R.string.appearance_dark })
        }, enabled = enabled) { value -> onChange { it.copy(appearance = value) } }
        ThemeSwitch(stringResource(R.string.theme_dynamic), stringResource(R.string.theme_dynamic_hint), settings.dynamicTheme && Build.VERSION.SDK_INT >= 31,
            enabled = enabled && Build.VERSION.SDK_INT >= 31) { value -> onChange { it.copy(dynamicTheme = value) } }
        if (!settings.dynamicTheme || Build.VERSION.SDK_INT < 31) {
            ThemeSeedPicker(settings.themeSeed, enabled) { value -> onChange { it.copy(themeSeed = value) } }
        }
        SettingsChoiceRow(stringResource(R.string.theme_palette), settings.themePalette, ThemePalette.entries.associateWith {
            stringResource(when (it) {
                ThemePalette.TONAL_SPOT -> R.string.theme_palette_tonal; ThemePalette.NEUTRAL -> R.string.theme_palette_neutral
                ThemePalette.VIBRANT -> R.string.theme_palette_vibrant; ThemePalette.EXPRESSIVE -> R.string.theme_palette_expressive
                ThemePalette.RAINBOW -> R.string.theme_palette_rainbow; ThemePalette.FRUIT_SALAD -> R.string.theme_palette_fruit
                ThemePalette.MONOCHROME -> R.string.theme_palette_mono; ThemePalette.FIDELITY -> R.string.theme_palette_fidelity
                ThemePalette.CONTENT -> R.string.theme_palette_content
            })
        }, enabled = enabled) { value -> onChange { it.copy(themePalette = value) } }
        SettingsChoiceRow(stringResource(R.string.theme_color_spec), settings.themeColorSpec, ThemeColorSpec.entries.associateWith {
            stringResource(if (it == ThemeColorSpec.SPEC_2025) R.string.theme_spec_2025 else R.string.theme_spec_2021)
        }, enabled = enabled, supportingText = stringResource(R.string.theme_spec_hint)) { value ->
            onChange { it.copy(themeColorSpec = value) }
        }
        SettingsChoiceRow(stringResource(R.string.theme_contrast), settings.themeContrast, ThemeContrast.entries.associateWith {
            stringResource(when (it) { ThemeContrast.STANDARD -> R.string.theme_contrast_standard; ThemeContrast.MEDIUM -> R.string.theme_contrast_medium; ThemeContrast.HIGH -> R.string.theme_contrast_high })
        }, enabled = enabled) { value -> onChange { it.copy(themeContrast = value) } }
        ThemeSwitch(stringResource(R.string.theme_black), stringResource(R.string.theme_black_hint), settings.pureBlackTheme,
            enabled = enabled) { value ->
            onChange { it.copy(pureBlackTheme = value) }
        }
    }
    ThemeSettingsSection(stringResource(R.string.theme_navigation)) {
        ThemeSwitch(stringResource(R.string.glass_navigation), stringResource(R.string.theme_glass_hint), settings.glassNavigation && Build.VERSION.SDK_INT >= 33,
            enabled = enabled && Build.VERSION.SDK_INT >= 33) { value -> onChange { it.copy(glassNavigation = value) } }
        ThemeSwitch(stringResource(R.string.theme_navigation_blur), stringResource(R.string.theme_navigation_blur_hint), settings.blurNavigation && Build.VERSION.SDK_INT >= 31,
            enabled = enabled && Build.VERSION.SDK_INT >= 31) { value -> onChange { it.copy(blurNavigation = value) } }
        if (Build.VERSION.SDK_INT < 33) Text(stringResource(R.string.theme_effect_support), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ThemeSettingsSection(stringResource(R.string.theme_back)) {
        SettingsChoiceRow(stringResource(R.string.theme_back_style), settings.predictiveBackStyle, PredictiveBackStyle.entries.associateWith {
            stringResource(when (it) { PredictiveBackStyle.SYSTEM -> R.string.theme_back_system; PredictiveBackStyle.SLIDE -> R.string.theme_back_slide
                PredictiveBackStyle.SCALE -> R.string.theme_back_scale; PredictiveBackStyle.NONE -> R.string.theme_back_none })
        }, enabled = enabled, supportingText = stringResource(R.string.theme_back_hint)) { value ->
            onChange { it.copy(predictiveBackStyle = value) }
        }
    }
}

@Composable
private fun ThemeSettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = FuyaoSpacing.cardInset),
        verticalArrangement = Arrangement.spacedBy(FuyaoSpacing.compact),
    ) {
        SectionHeading(title)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemePreview(colors: ColorScheme) {
    Surface(color = colors.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(
            Modifier.fillMaxWidth().padding(FuyaoSpacing.cardInset),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.theme_preview), color = colors.onSurface, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    R.string.theme_primary to colors.primary,
                    R.string.theme_secondary to colors.secondary,
                    R.string.theme_tertiary to colors.tertiary,
                ).forEach { (label, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(24.dp).background(color, MaterialTheme.shapes.small))
                        Text(stringResource(label), color = colors.onSurface, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSwitch(title: String, hint: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSeedPicker(seed: Long, enabled: Boolean, onSelect: (Long) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable(seed) { mutableStateOf("%06X".format(seed and 0xFFFFFF)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.theme_seed), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                TextButton({ input = "%06X".format(seed and 0xFFFFFF); editing = true }, enabled = enabled) {
                    Text(stringResource(R.string.theme_seed_custom))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0xFF6750A4, 0xFF006F6B, 0xFF0061A4, 0xFF895000, 0xFF984061, 0xFF506600).forEach { value ->
                FilterChip(selected = seed == value, onClick = { onSelect(value) }, enabled = enabled,
                    label = { Text("#%06X".format(value and 0xFFFFFF)) },
                    leadingIcon = { Box(Modifier.size(18.dp).background(Color(value), MaterialTheme.shapes.small)) })
            }
        }
    }
    if (editing) {
        val valid = input.matches(Regex("[0-9a-fA-F]{6}"))
        AlertDialog(onDismissRequest = { editing = false }, title = { Text(stringResource(R.string.theme_seed_custom)) },
            text = { OutlinedTextField(input, { if (it.length <= 6) input = it }, enabled = enabled, singleLine = true,
                label = { Text(stringResource(R.string.theme_seed_format)) }, prefix = { Text("#") }, isError = !valid,
                supportingText = { if (!valid) Text(stringResource(R.string.theme_seed_invalid)) }) },
            confirmButton = { TextButton({ onSelect(0xFF000000 or input.toLong(16)); editing = false }, enabled = enabled && valid) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton({ editing = false }) { Text(stringResource(android.R.string.cancel)) } })
    }
}
