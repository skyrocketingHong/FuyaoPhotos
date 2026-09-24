package ing.fuyaoskyrocket.photoinfo.domain.metadata

import ing.fuyaoskyrocket.photoinfo.domain.lens.LensProfile
import kotlin.math.ceil

/** EXIF identifies the photographed device; Camera2 IDs are only local hardware bindings. */
object AndroidLensMetadata {
    data class Lens(val camera: String, val focalLength: String, val deviceName: String = "")
    fun resolve(make: String, model: String, lensModel: String, equivalentMm: Double,
        fallbackMainMm: Double? = null, profiles: List<LensProfile> = emptyList(), physicalMm: Double = 0.0): Lens {
        val device = MetadataFormatting.device(make, model)
        val isFront = Regex("(?i)\\b(front|selfie)\\b").containsMatchIn(lensModel)
        val deviceProfiles = profiles.filter { model.isNotBlank() && it.valid() && (it.acceptsExif(model) || it.acceptsExif(device)) }
        val candidates = deviceProfiles.filter { !isFront || it.facing != "BACK" }
        val physical = physicalMm.takeIf { it.isFinite() && it > 0 }
        val equivalents = candidates.mapNotNull { p ->
            if (p.physicalMin != null && physical != null && !p.containsPhysical(physical)) return@mapNotNull null
            val mm = equivalentMm.takeIf { it.isFinite() && it > 0 } ?: physical?.let(p::equivalentFor)
            if (mm == null || mm < p.equivalentMin - .5) null else p to mm
        }
        // Actual lens focal length does not increase when its image is digitally cropped.
        val identified = equivalents.filter { (p, _) -> physical != null && p.containsPhysical(physical) }
        val match = if (identified.isNotEmpty()) identified.singleOrNull() else equivalents.filter { (p, mm) ->
            (!isFront || p.facing == "FRONT") && covers(p, mm, candidates)
        }.singleOrNull()
        val mm = equivalentMm.takeIf { it.isFinite() && it > 0 } ?: match?.second ?: 0.0
        val product = match?.first?.device ?: deviceProfiles.map { it.device }.distinct().singleOrNull().orEmpty()
        val focal = MetadataFormatting.equivalentFocalLength(mm)
        if (focal.isEmpty()) return Lens(lensModel, "", product)
        val explicitZoom = Regex("(?i)(?:^|[\\s(])([0-9]+(?:\\.[0-9]+)?)\\s*[x×](?:$|[\\s)])")
            .find(lensModel)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        val base = fallbackMainMm?.takeIf { !isFront && it.isFinite() && it in 1.0..200.0 }
        val zoom = explicitZoom ?: match?.first?.zoomFor(mm) ?: base?.let { mm / it }
        return Lens(lensModel.ifBlank { match?.first?.name.orEmpty() },
            if (zoom == null) focal else "$focal (${MetadataFormatting.number(zoom, 1)}X)", product)
    }

    private fun covers(p: LensProfile, mm: Double, peers: List<LensProfile>): Boolean {
        if (p.containsNative(mm)) return true
        if (mm <= p.equivalentMax) return false
        val zoom = p.zoomFor(mm)
        p.digitalZoomMax?.let { return zoom != null && zoom < it + .05 }
        // Infer crop coverage only up to the next lens of the same direction.
        // A last lens with no explicit limit is not assumed to support unlimited digital zoom.
        fun group(lens: LensProfile) = lens.facing.ifBlank { "BACK" }
        val next = peers.filter { it.id != p.id && group(it) == group(p) && it.equivalentMin > p.equivalentMax }
            .minByOrNull { it.equivalentMin } ?: return false
        if (mm >= next.equivalentMin - .5) return false
        if (zoom != null && next.zoomMin != null && p.zoomMax != null && next.zoomMin > p.zoomMax) {
            val maxDisplayZoom = (ceil(next.zoomMin * 10 - 1e-6) - 1) / 10
            if (zoom >= maxDisplayZoom + .05) return false
        }
        return true
    }
}
