package ing.fuyaoskyrocket.photoinfo.domain.metadata

import ing.fuyaoskyrocket.photoinfo.domain.lens.LensProfile

/** Camera IDs are local hardware identifiers, not EXIF identifiers. Match device + configured focal range. */
object AndroidLensMetadata {
    data class Lens(val camera: String, val focalLength: String)
    fun resolve(make: String, model: String, lensModel: String, equivalentMm: Double,
        fallbackMainMm: Double? = null, profiles: List<LensProfile> = emptyList(), physicalMm: Double = 0.0): Lens {
        val device = MetadataFormatting.device(make,model)
        val candidates = profiles.filter { it.valid() && (it.acceptsDevice(device) || it.acceptsDevice(model)) }
        val equivalents = candidates.mapNotNull { p ->
            // Equivalent focal length alone cannot distinguish optical telephoto from a main-camera crop.
            if (p.physicalMin != null && physicalMm.isFinite() && physicalMm > 0 && !p.containsPhysical(physicalMm)) return@mapNotNull null
            val mm = equivalentMm.takeIf { it.isFinite() && it > 0 } ?: p.equivalentFor(physicalMm)
            if (mm != null && mm in (p.equivalentMin-.5)..(p.equivalentMax+.5)) p to mm else null
        }
        // Overlapping ranges remain ambiguous; do not assign the first lens arbitrarily.
        val match = equivalents.singleOrNull()
        val mm = equivalentMm.takeIf { it.isFinite() && it > 0 } ?: match?.second ?: 0.0
        val focal = MetadataFormatting.equivalentFocalLength(mm)
        if (focal.isEmpty()) return Lens(lensModel, "")
        val explicitZoom = Regex("(?i)(?:^|[\\s(])([0-9]+(?:\\.[0-9]+)?)\\s*[x×](?:$|[\\s)])")
            .find(lensModel)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        val isFront = Regex("(?i)\\b(front|selfie)\\b").containsMatchIn(lensModel)
        val base = fallbackMainMm?.takeIf { !isFront && it.isFinite() && it in 1.0..200.0 }
        val zoom = explicitZoom ?: match?.first?.zoomFor(mm) ?: base?.let { mm/it }
        return Lens(lensModel.ifBlank { match?.first?.name.orEmpty() },
            if (zoom == null) focal else "$focal (${MetadataFormatting.number(zoom,1)}X)")
    }
}
