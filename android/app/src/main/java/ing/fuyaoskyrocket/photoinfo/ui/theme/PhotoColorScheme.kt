package ing.fuyaoskyrocket.photoinfo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.*
import ing.fuyaoskyrocket.photoinfo.domain.model.ThemeColorSpec
import ing.fuyaoskyrocket.photoinfo.domain.model.ThemeContrast
import ing.fuyaoskyrocket.photoinfo.domain.model.ThemePalette

internal fun photoColorScheme(
    seed: Int,
    dark: Boolean,
    palette: ThemePalette,
    contrast: ThemeContrast,
    colorSpec: ThemeColorSpec,
    pureBlack: Boolean,
): ColorScheme {
    val hct = Hct.fromInt(seed)
    val spec = if (colorSpec == ThemeColorSpec.SPEC_2025) ColorSpec.SpecVersion.SPEC_2025 else ColorSpec.SpecVersion.SPEC_2021
    val scheme = when (palette) {
        ThemePalette.TONAL_SPOT -> SchemeTonalSpot(hct, dark, contrast.level, spec)
        ThemePalette.NEUTRAL -> SchemeNeutral(hct, dark, contrast.level, spec)
        ThemePalette.VIBRANT -> SchemeVibrant(hct, dark, contrast.level, spec)
        ThemePalette.EXPRESSIVE -> SchemeExpressive(hct, dark, contrast.level, spec)
        ThemePalette.RAINBOW -> SchemeRainbow(hct, dark, contrast.level, spec)
        ThemePalette.FRUIT_SALAD -> SchemeFruitSalad(hct, dark, contrast.level, spec)
        ThemePalette.MONOCHROME -> SchemeMonochrome(hct, dark, contrast.level, spec)
        ThemePalette.FIDELITY -> SchemeFidelity(hct, dark, contrast.level, spec)
        ThemePalette.CONTENT -> SchemeContent(hct, dark, contrast.level, spec)
    }
    return scheme.composeColors().let { colors ->
        if (dark && pureBlack) colors.copy(background = Color.Black, surface = Color.Black,
            surfaceDim = Color.Black, surfaceContainerLowest = Color.Black)
        else colors
    }
}

private fun DynamicScheme.composeColors() = lightColorScheme(
    primary = Color(primary), onPrimary = Color(onPrimary), primaryContainer = Color(primaryContainer),
    onPrimaryContainer = Color(onPrimaryContainer), inversePrimary = Color(inversePrimary),
    secondary = Color(secondary), onSecondary = Color(onSecondary), secondaryContainer = Color(secondaryContainer),
    onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary), onTertiary = Color(onTertiary), tertiaryContainer = Color(tertiaryContainer),
    onTertiaryContainer = Color(onTertiaryContainer),
    background = Color(background), onBackground = Color(onBackground),
    surface = Color(surface), onSurface = Color(onSurface), surfaceVariant = Color(surfaceVariant),
    onSurfaceVariant = Color(onSurfaceVariant), surfaceTint = Color(primary),
    inverseSurface = Color(inverseSurface), inverseOnSurface = Color(inverseOnSurface),
    error = Color(error), onError = Color(onError), errorContainer = Color(errorContainer), onErrorContainer = Color(onErrorContainer),
    outline = Color(outline), outlineVariant = Color(outlineVariant), scrim = Color(scrim),
    surfaceBright = Color(surfaceBright), surfaceDim = Color(surfaceDim),
    surfaceContainer = Color(surfaceContainer), surfaceContainerHigh = Color(surfaceContainerHigh),
    surfaceContainerHighest = Color(surfaceContainerHighest), surfaceContainerLow = Color(surfaceContainerLow),
    surfaceContainerLowest = Color(surfaceContainerLowest),
    primaryFixed = Color(primaryFixed), primaryFixedDim = Color(primaryFixedDim),
    onPrimaryFixed = Color(onPrimaryFixed), onPrimaryFixedVariant = Color(onPrimaryFixedVariant),
    secondaryFixed = Color(secondaryFixed), secondaryFixedDim = Color(secondaryFixedDim),
    onSecondaryFixed = Color(onSecondaryFixed), onSecondaryFixedVariant = Color(onSecondaryFixedVariant),
    tertiaryFixed = Color(tertiaryFixed), tertiaryFixedDim = Color(tertiaryFixedDim),
    onTertiaryFixed = Color(onTertiaryFixed), onTertiaryFixedVariant = Color(onTertiaryFixedVariant),
)
