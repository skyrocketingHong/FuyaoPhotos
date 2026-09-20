package ing.fuyaoskyrocket.photoinfo.domain.lens

import java.util.Locale

data class LensProfile(
    val id: String,
    val device: String,
    val name: String,
    val cameraId: String = "",
    val equivalentMin: Double,
    val equivalentMax: Double,
    val zoomMin: Double? = null,
    val zoomMax: Double? = null,
    val physicalMin: Double? = null,
    val physicalMax: Double? = null,
) {
    fun valid(): Boolean = id.isNotBlank() && device.isNotBlank() && device.length <= 256 && name.isNotBlank() && name.length <= 256 &&
        range(equivalentMin, equivalentMax, 2000.0) && optionalRange(zoomMin, zoomMax, 200.0) && optionalRange(physicalMin, physicalMax, 1000.0)
    fun acceptsDevice(value: String) = normalize(device) == normalize(value)
    fun equivalentFor(physical: Double): Double? = physicalMin?.let { low -> physicalMax?.let { high ->
        if (physical in (low - .02)..(high + .02)) interpolate(physical.coerceIn(low, high), low, high, equivalentMin, equivalentMax) else null
    } }
    fun zoomFor(equivalent: Double): Double? = zoomMin?.let { low -> zoomMax?.let { high ->
        interpolate(equivalent.coerceIn(equivalentMin, equivalentMax), equivalentMin, equivalentMax, low, high)
    } }
    companion object {
        fun normalize(value: String) = value.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        private fun range(min: Double, max: Double, limit: Double) = min.isFinite() && max.isFinite() && min > 0 && max >= min && max <= limit
        private fun optionalRange(min: Double?, max: Double?, limit: Double) = (min == null && max == null) || (min != null && max != null && range(min,max,limit))
        private fun interpolate(value: Double, min: Double, max: Double, outMin: Double, outMax: Double) =
            if (max == min) outMin else outMin + (value-min)/(max-min)*(outMax-outMin)
    }
}
