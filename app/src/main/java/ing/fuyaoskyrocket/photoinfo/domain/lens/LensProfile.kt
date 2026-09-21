package ing.fuyaoskyrocket.photoinfo.domain.lens

import java.util.Locale

/** Optical calibration and digital crop coverage are separate from the product's display name. */
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
    val exifModel: String = device,
    val hardwareDevice: String = "",
    val digitalZoomMax: Double? = null,
    val facing: String = "",
) {
    fun valid(): Boolean = id.isNotBlank() && device.isNotBlank() && device.length <= 256 && name.isNotBlank() && name.length <= 256 &&
        exifModel.isNotBlank() && exifModel.length <= 256 && cameraId.length <= 256 && hardwareDevice.length <= 512 &&
        facing in listOf("", "BACK", "FRONT", "EXTERNAL") &&
        range(equivalentMin, equivalentMax, 2000.0) && optionalRange(zoomMin, zoomMax, 200.0) && optionalRange(physicalMin, physicalMax, 1000.0) &&
        (equivalentMin != equivalentMax || zoomMin == null || zoomMin == zoomMax) &&
        (digitalZoomMax == null || (zoomMax != null && digitalZoomMax.isFinite() && digitalZoomMax in zoomMax..200.0))

    fun acceptsExif(value: String) = normalize(exifModel) == normalize(value)
    fun containsPhysical(physical: Double) = physical.isFinite() && physicalMin != null && physicalMax != null && physical in (physicalMin - .02)..(physicalMax + .02)
    fun containsNative(equivalent: Double) = equivalent in (equivalentMin - .5)..(equivalentMax + .5)
    fun equivalentFor(physical: Double): Double? {
        val low = physicalMin ?: return null
        val high = physicalMax ?: return null
        if (!containsPhysical(physical) || (low == high && equivalentMin != equivalentMax)) return null
        return interpolate(physical.coerceIn(low, high), low, high, equivalentMin, equivalentMax)
    }
    fun zoomFor(equivalent: Double): Double? = zoomMin?.let { low -> zoomMax?.let { high ->
        when {
            !equivalent.isFinite() || equivalent <= 0 -> null
            equivalent < equivalentMin -> low * equivalent / equivalentMin
            equivalent > equivalentMax -> high * equivalent / equivalentMax
            else -> interpolate(equivalent, equivalentMin, equivalentMax, low, high)
        }
    } }

    /** Old profiles used one device field and occasionally put crop limits in fixed-lens calibration. */
    fun upgradeLegacy(localHardwareDevice: String = ""): LensProfile {
        val fixedCrop = equivalentMin == equivalentMax && zoomMin != null && zoomMax != null && zoomMax > zoomMin
        return copy(exifModel = device, hardwareDevice = if (cameraId.isBlank()) "" else localHardwareDevice,
            zoomMax = if (fixedCrop) zoomMin else zoomMax, digitalZoomMax = if (fixedCrop) zoomMax else digitalZoomMax)
    }
    fun boundTo(hardware: String, lensId: String) = cameraId.isNotBlank() && hardwareDevice.isNotBlank() && hardwareDevice == hardware && cameraId == lensId

    companion object {
        fun normalize(value: String) = value.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        private fun range(min: Double, max: Double, limit: Double) = min.isFinite() && max.isFinite() && min > 0 && max >= min && max <= limit
        private fun optionalRange(min: Double?, max: Double?, limit: Double) = (min == null && max == null) || (min != null && max != null && range(min, max, limit))
        private fun interpolate(value: Double, min: Double, max: Double, outMin: Double, outMax: Double) = if (max == min) outMin else outMin + (value - min) / (max - min) * (outMax - outMin)
    }
}
