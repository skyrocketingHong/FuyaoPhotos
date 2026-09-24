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
    fun place(locality: String?, subAdmin: String?, admin: String?, country: String?, countryCode: String?): String {
        val city = listOf(locality, subAdmin, admin).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
        val nation = countryCode?.trim()?.takeIf { it.matches(Regex("[A-Za-z]{2}")) }
            ?.let { Locale.Builder().setRegion(it.uppercase(Locale.ROOT)).build().getDisplayCountry(Locale.ENGLISH) }
            ?.takeIf { it.isNotBlank() } ?: country.orEmpty().trim()
        return listOf(city, nation).filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.ROOT) }.joinToString(", ")
    }
}
