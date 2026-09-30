package ing.fuyaoskyrocket.photoinfo.domain.lens

data class HardwareLens(val id: String, val facing: String, val physicalFocals: List<Double>, val apertures: List<Double>)
