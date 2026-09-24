package ing.fuyaoskyrocket.photoinfo.domain.lens

object LensBindings {
    fun associated(profiles: List<LensProfile>, hardwareDevice: String, cameraId: String) =
        profiles.filter { it.boundTo(hardwareDevice, cameraId) }
    fun unconfigured(cameraIds: List<String>, profiles: List<LensProfile>, hardwareDevice: String) =
        cameraIds.distinct().filter { associated(profiles, hardwareDevice, it).isEmpty() }
    fun hasDuplicates(profiles: List<LensProfile>): Boolean {
        val bound = profiles.filter { it.cameraId.isNotBlank() && it.hardwareDevice.isNotBlank() }
        return bound.map { it.hardwareDevice to it.cameraId }.distinct().size != bound.size
    }
}
