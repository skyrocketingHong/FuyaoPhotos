package ing.fuyaoskyrocket.photoinfo.domain.lens

/** Versioned saveable editor state; existing ten-field drafts remain readable. */
object LensProfileFields {
    const val VERSION = "lens-v3"
    const val SIZE = 16
    fun encode(p: LensProfile) = arrayListOf(VERSION, p.id, p.device, p.name, p.cameraId,
        p.equivalentMin.toString(), p.equivalentMax.toString(), p.zoomMin?.toString().orEmpty(), p.zoomMax?.toString().orEmpty(),
        p.physicalMin?.toString().orEmpty(), p.physicalMax?.toString().orEmpty(), p.exifModel, p.hardwareDevice, p.digitalZoomMax?.toString().orEmpty(), p.facing, p.hardwareModel)
    fun decode(fields: List<String>): LensProfile {
        val modern = fields.firstOrNull() in setOf(VERSION, "lens-v2")
        require(fields.size == sizeFor(fields.firstOrNull()))
        val p = if (modern) fields.drop(1) else fields
        val lens = LensProfile(p[0], p[1], p[2], p[3], p[4].toDouble(), p[5].toDouble(),
            p[6].toDoubleOrNull(), p[7].toDoubleOrNull(), p[8].toDoubleOrNull(), p[9].toDoubleOrNull())
        return if (modern) lens.copy(exifModel=p[10], hardwareDevice=p[11], digitalZoomMax=p[12].toDoubleOrNull(), facing=p[13], hardwareModel=p.getOrNull(14).orEmpty()) else lens.upgradeLegacy()
    }
    fun decodeList(fields: List<String>): List<LensProfile> {
        val size = sizeFor(fields.firstOrNull())
        require(fields.size % size == 0)
        return fields.chunked(size).map(::decode)
    }
    fun sizeFor(version: String?) = when (version) { VERSION -> SIZE; "lens-v2" -> 15; else -> 10 }
}
