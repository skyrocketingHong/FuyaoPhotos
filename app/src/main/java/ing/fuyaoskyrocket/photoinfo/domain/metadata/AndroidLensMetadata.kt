package ing.fuyaoskyrocket.photoinfo.domain.metadata

import java.util.Locale
import kotlin.math.abs

/** Only equivalent focal lengths participate; DigitalZoomRatio is NOT total camera magnification. */
object AndroidLensMetadata {
    data class Lens(val camera: String, val focalLength: String)
    private data class Profile(val main: Double, val tele: ClosedFloatingPointRange<Double>)

    fun resolve(make: String, model: String, lensModel: String, equivalentMm: Double, fallbackMainMm: Double? = null): Lens {
        val focal = MetadataFormatting.equivalentFocalLength(equivalentMm)
        if (focal.isEmpty()) return Lens(lensModel, "")
        val name = MetadataFormatting.device(make, model).uppercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ").removeSuffix(" BY LEICA").trim()
        // Sources and display-magnification conventions are recorded in docs/METADATA.md.
        val isFrontCamera = Regex("(?i)\\b(front|selfie)\\b").containsMatchIn(lensModel)
        val profile = if (isFrontCamera) null else when (name) {
            "XIAOMI 17 ULTRA" -> Profile(23.0, 75.0..100.0)
            "XIAOMI 14" -> Profile(23.0, 75.0..75.0)
            else -> null
        }
        val explicitZoom = Regex("(?i)(?:^|[\\s(])([0-9]+(?:\\.[0-9]+)?)\\s*[x×](?:$|[\\s)])")
            .find(lensModel)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        val base = if (isFrontCamera) null else if (profile != null) {
            // Do not describe a 21mm selfie as 0.9x relative to the rear main camera.
            profile.main.takeIf { equivalentMm >= it || abs(equivalentMm - 14.0) < .5 }
        } else fallbackMainMm?.takeIf { it.isFinite() && it in 1.0..200.0 }
        val zoom = explicitZoom ?: when {
            profile != null && abs(equivalentMm - 75.0) < .5 -> 3.2
            base != null -> equivalentMm / base
            else -> null
        }
        val camera = lensModel.ifBlank {
            when {
                profile == null -> ""
                abs(equivalentMm - 14.0) < .5 -> "LEICA ULTRA WIDE"
                abs(equivalentMm - profile.main) < .5 -> "LEICA MAIN"
                equivalentMm in profile.tele -> "LEICA TELEPHOTO"
                else -> ""
            }
        }
        return Lens(camera, if (zoom == null) focal else "$focal (${MetadataFormatting.number(zoom, 1)}X)")
    }
}
