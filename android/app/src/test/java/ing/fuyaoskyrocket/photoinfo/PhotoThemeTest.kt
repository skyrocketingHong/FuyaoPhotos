package ing.fuyaoskyrocket.photoinfo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import ing.fuyaoskyrocket.photoinfo.domain.model.ThemeColorSpec
import ing.fuyaoskyrocket.photoinfo.domain.model.ThemeContrast
import ing.fuyaoskyrocket.photoinfo.domain.model.ThemePalette
import ing.fuyaoskyrocket.photoinfo.ui.theme.photoColorScheme
import org.junit.Assert.*
import org.junit.Test

class PhotoThemeTest {
    @Test fun palettePairsKeepTextContrastAcrossModesAndSpecifications() {
        for (palette in ThemePalette.entries) for (spec in ThemeColorSpec.entries) for (dark in listOf(false, true)) {
            val colors = photoColorScheme(0xFF006F6B.toInt(), dark, palette, ThemeContrast.STANDARD, spec, false)
            assertTrue("$palette $spec dark=$dark primary text", contrast(colors.primary, colors.onPrimary) >= 4.5f)
            assertTrue("$palette $spec dark=$dark surface text", contrast(colors.surface, colors.onSurface) >= 4.5f)
        }
    }

    @Test fun customSeedAndPaletteChangeGeneratedRoles() {
        fun colors(seed: Int, palette: ThemePalette) = photoColorScheme(seed, false, palette, ThemeContrast.STANDARD, ThemeColorSpec.SPEC_2025, false)
        val teal = colors(0xFF006F6B.toInt(), ThemePalette.TONAL_SPOT)
        val purple = colors(0xFF6750A4.toInt(), ThemePalette.TONAL_SPOT)
        val expressive = colors(0xFF006F6B.toInt(), ThemePalette.EXPRESSIVE)
        assertNotEquals(teal.primary.toArgb(), purple.primary.toArgb())
        assertNotEquals(teal.primary.toArgb(), expressive.primary.toArgb())
    }

    @Test fun blackModeOnlyChangesDarkPageSurfaces() {
        fun colors(dark: Boolean, black: Boolean) = photoColorScheme(0xFF6750A4.toInt(), dark, ThemePalette.TONAL_SPOT, ThemeContrast.STANDARD, ThemeColorSpec.SPEC_2025, black)
        val light = colors(false, false)
        val lightWithBlackPreference = colors(false, true)
        assertEquals(light.surface, lightWithBlackPreference.surface)
        assertEquals(light.background, lightWithBlackPreference.background)
        assertEquals(light.primary, lightWithBlackPreference.primary)
        val original = colors(true, false)
        val black = colors(true, true)
        assertEquals(Color.Black, black.surface)
        assertEquals(original.primary, black.primary)
        assertEquals(original.surfaceContainerLow, black.surfaceContainerLow)
        assertTrue(contrast(black.surface, black.onSurface) >= 4.5f)
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
    }
}
