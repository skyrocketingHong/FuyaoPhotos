package ing.fuyaoskyrocket.photoinfo.domain.metadata

import java.util.Locale
import kotlin.math.abs

object AppleCameraNames {
    data class Camera(val name: String, val focalLength: Double, val nativeZoom: Double) {
        fun zoomAt(equivalent: Double): Double? = equivalent.takeIf { it.isFinite() && it > 0 }
            ?.let { it / focalLength * nativeZoom }
    }

    fun resolve(make: String, model: String, lens: String, cameraType: Int?): Camera? {
        if (!make.trim().equals("Apple", true)) return null
        val product = model.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        val pro17 = product in setOf("iphone 17 pro", "iphone 17 pro max")
        val pro18 = product in setOf("iphone 18 pro", "iphone 18 pro max")
        val air = product == "iphone air"
        if (!pro17 && !pro18 && !air) return null
        val description = lens.lowercase(Locale.ROOT)
        if (cameraType == 6 || "front" in description || "selfie" in description) return null
        val role = when {
            cameraType == 0 -> 0
            cameraType == 1 -> 1
            "ultra wide" in description -> 0
            "telephoto" in description -> 2
            "fusion main" in description -> 1
            else -> {
                if ("back" !in description) return null
                // LensModel's maximum aperture stays separate from the actual shot's FNumber.
                val aperture = Regex("f/([0-9]+(?:\\.[0-9]+)?)$").find(description)
                    ?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
                when {
                    abs(aperture - (if (pro18) 1.48 else if (air) 1.6 else 1.78)) < .001 -> 1
                    !air && abs(aperture - 2.2) < .001 -> 0
                    !air && abs(aperture - 2.8) < .001 -> 2
                    else -> return null
                }
            }
        }
        return when {
            role == 0 && !air -> Camera("Fusion Ultra Wide", 13.0, .5)
            role == 1 -> Camera("Fusion Main", if (air) 26.0 else 24.0, 1.0)
            role == 2 && !air -> Camera("Fusion Telephoto", 100.0, 4.0)
            else -> null
        }
    }

    fun displayLensName(lens: String, model: String): String {
        val device = model.trim().replace(Regex("\\s+"), " ")
        if (device.isEmpty() || !lens.startsWith(device, true)) return lens
        val suffix = lens.drop(device.length)
        if (suffix.isNotEmpty() && !suffix.first().isWhitespace()) return lens
        val remainder = suffix.trimStart()
        return if (remainder.isEmpty() || remainder.first().isLowerCase()) remainder else lens
    }
}
