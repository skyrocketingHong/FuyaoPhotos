package ing.fuyaoskyrocket.photoinfo.domain.model

enum class PhotoFeature { CARDS, METADATA, COLORS }
enum class PhotoSharing { ALL, INDEPENDENT, PARTIAL }
enum class StartPage { MAP, EDITOR, METADATA, COLORS }
enum class AppAppearance { SYSTEM, LIGHT, DARK;
    fun isDark(systemDark: Boolean): Boolean = when (this) { SYSTEM -> systemDark; LIGHT -> false; DARK -> true }
}

data class WorkspaceSettings(
    val startPage: StartPage = StartPage.EDITOR,
    val sharing: PhotoSharing = PhotoSharing.INDEPENDENT,
    val sharedFeatures: Set<PhotoFeature> = setOf(PhotoFeature.CARDS, PhotoFeature.METADATA),
    val appearance: AppAppearance = AppAppearance.SYSTEM,
    val glassNavigation: Boolean = true,
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
