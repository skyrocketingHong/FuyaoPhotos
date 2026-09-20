package ing.fuyaoskyrocket.photoinfo.data.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics as C
import android.hardware.camera2.CameraManager
import android.os.Build

data class HardwareLens(val id: String, val facing: String, val physicalFocals: List<Double>, val apertures: List<Double>)
data class CameraInventory(val lenses: List<HardwareLens>, val logicalCount: Int, val incomplete: Boolean)

class CameraInventoryReader(context: Context) {
    private val manager = context.getSystemService(CameraManager::class.java)
    fun scan(): CameraInventory {
        val lenses = linkedMapOf<String,HardwareLens>()
        var logical = 0
        var incomplete = false
        for (id in manager.cameraIdList) {
            val c = runCatching { manager.getCameraCharacteristics(id) }.getOrNull()
            if (c == null) { incomplete = true; continue }
            val physical = if (Build.VERSION.SDK_INT >= 28) c.physicalCameraIds else emptySet()
            if (physical.isNotEmpty()) logical++
            for (sensorId in physical.ifEmpty { setOf(id) }) {
                val sensor = runCatching { manager.getCameraCharacteristics(sensorId) }.getOrNull()
                if (sensor == null) incomplete = true
                val facing = when ((sensor ?: c)[C.LENS_FACING]) { C.LENS_FACING_FRONT -> "FRONT"; C.LENS_FACING_BACK -> "BACK"; else -> "EXTERNAL" }
                lenses[sensorId] = HardwareLens(sensorId, facing,
                    sensor?.get(C.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.map { it.toDouble() }.orEmpty(),
                    sensor?.get(C.LENS_INFO_AVAILABLE_APERTURES)?.map { it.toDouble() }.orEmpty())
            }
        }
        return CameraInventory(lenses.values.toList(),logical,incomplete)
    }
}
