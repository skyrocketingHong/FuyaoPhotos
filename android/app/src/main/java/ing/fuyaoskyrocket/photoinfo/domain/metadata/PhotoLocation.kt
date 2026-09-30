package ing.fuyaoskyrocket.photoinfo.domain.metadata

import java.util.Locale

data class PhotoCoordinates(val latitude: Double, val longitude: Double) {
    companion object {
        fun from(latitude: Double, longitude: Double): PhotoCoordinates? =
            if (latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0)
                PhotoCoordinates(latitude, longitude) else null
    }
}

object LocationFormatting {
    private val countryCodes = Locale.getISOCountries().toSet()

    fun place(locality: String?, country: String?, countryCode: String?): String {
        fun clean(value: String?) = value.orEmpty().trim().replace(Regex("\\s+"), " ")
        val city = clean(locality)
        val code = countryCode?.trim()?.uppercase(Locale.ROOT)
        val nation = if (code == "CN") "China" else code?.takeIf { it in countryCodes }
            ?.let { Locale.Builder().setRegion(it.uppercase(Locale.ROOT)).build().getDisplayCountry(Locale.ENGLISH) }
            ?.takeIf { it.isNotBlank() } ?: country
        return listOf(city, clean(nation)).filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.ROOT) }.joinToString(", ")
    }
}
