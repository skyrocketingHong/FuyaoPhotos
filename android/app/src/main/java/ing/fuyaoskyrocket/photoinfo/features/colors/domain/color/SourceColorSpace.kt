package ing.fuyaoskyrocket.photoinfo.features.colors.domain.color

internal enum class SourceColorSpace(val tabIndex: Int) {
    SRGB(0), DISPLAY_P3(1), REC2020(2), A98(3), LAB(4), XYZ(6);

    companion object {
        fun fromProfile(name: String?): SourceColorSpace {
            val key = name.orEmpty().lowercase().filter(Char::isLetterOrDigit)
            return when {
                "displayp3" in key -> DISPLAY_P3
                listOf("bt2020", "rec2020", "itur2020", "bt2100", "rec2100", "itur2100").any(key::contains) -> REC2020
                "adobergb" in key || "a98" in key -> A98
                "srgb" in key -> SRGB
                key == "lab" || "cielab" in key || "genericlab" in key -> LAB
                else -> XYZ
            }
        }
    }
}
