package ing.fuyaoskyrocket.photoinfo.domain.model

enum class PhotoFeature { CARDS, METADATA, COLORS }
enum class PhotoSharing { ALL, INDEPENDENT, PARTIAL }
enum class StartPage { MAP, EDITOR, METADATA, COLORS }
enum class AppAppearance { SYSTEM, LIGHT, DARK;
    fun isDark(systemDark: Boolean): Boolean = when (this) { SYSTEM -> systemDark; LIGHT -> false; DARK -> true }
}
enum class ThemePalette { TONAL_SPOT, NEUTRAL, VIBRANT, EXPRESSIVE, RAINBOW, FRUIT_SALAD, MONOCHROME, FIDELITY, CONTENT }
enum class ThemeContrast(val level: Double) { STANDARD(0.0), MEDIUM(0.5), HIGH(1.0) }
enum class ThemeColorSpec { SPEC_2021, SPEC_2025 }
enum class PredictiveBackStyle { SYSTEM, SLIDE, SCALE, NONE }

data class WorkspaceSettings(
    val startPage: StartPage = StartPage.EDITOR,
    val sharing: PhotoSharing = PhotoSharing.INDEPENDENT,
    val sharedFeatures: Set<PhotoFeature> = setOf(PhotoFeature.CARDS, PhotoFeature.METADATA),
    val appearance: AppAppearance = AppAppearance.SYSTEM,
    val glassNavigation: Boolean = true,
    val blurNavigation: Boolean = true,
    val dynamicTheme: Boolean = true,
    val themeSeed: Long = 0xFF6750A4,
    val themePalette: ThemePalette = ThemePalette.TONAL_SPOT,
    val themeContrast: ThemeContrast = ThemeContrast.STANDARD,
    val themeColorSpec: ThemeColorSpec = ThemeColorSpec.SPEC_2025,
    val pureBlackTheme: Boolean = false,
    val predictiveBackStyle: PredictiveBackStyle = PredictiveBackStyle.SYSTEM,
) {
    fun owner(feature: PhotoFeature): PhotoFeature {
        val members = when (sharing) {
            PhotoSharing.ALL -> PhotoFeature.entries.toSet()
            PhotoSharing.INDEPENDENT -> emptySet()
            PhotoSharing.PARTIAL -> sharedFeatures.takeIf { it.size >= 2 }.orEmpty()
        }
        return if (feature in members) members.minBy { it.ordinal } else feature
    }
}
