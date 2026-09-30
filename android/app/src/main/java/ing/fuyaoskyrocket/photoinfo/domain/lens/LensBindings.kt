package ing.fuyaoskyrocket.photoinfo.domain.lens

object LensBindings {
    data class DeviceGroup(val model: String, val profiles: List<LensProfile>, val isCurrent: Boolean) {
        val device: String get() = profiles.first().device
    }

    fun isCurrent(profile: LensProfile, hardwareDevice: String, aliases: Set<String>): Boolean = when {
        profile.hardwareDevice == hardwareDevice && hardwareDevice.isNotBlank() -> true
        profile.hardwareModel.isNotBlank() -> profile.hardwareModel == hardwareDevice
        else -> listOf(profile.device, profile.exifModel).any { LensProfile.normalize(it) in aliases.map(LensProfile::normalize) }
    }

    fun groups(profiles: List<LensProfile>, hardwareDevice: String, aliases: Set<String>) =
        profiles.groupBy { LensProfile.normalize(it.exifModel) }.map { (model, lenses) ->
            DeviceGroup(model, lenses, lenses.any { isCurrent(it, hardwareDevice, aliases) })
        }.sortedWith(compareByDescending<DeviceGroup> { it.isCurrent }
            .thenBy { LensProfile.normalize(it.device) }.thenBy { it.model })

    fun compatible(profile: LensProfile, hardware: HardwareLens, automatic: Boolean): Boolean {
        if (!profile.valid() || hardware.id.isBlank() || hardware.id.length > 256 || hardware.id.any(Char::isISOControl) ||
            hardware.facing !in setOf("BACK", "FRONT", "EXTERNAL")) return false
        if (profile.facing.isNotBlank() && profile.facing != hardware.facing) return false
        val focals = hardware.physicalFocals.filter { it.isFinite() && it > 0 }
        if (profile.physicalMin != null && profile.physicalMax != null && focals.isNotEmpty()) {
            return focals.any { focal ->
                val tolerance = maxOf(.05, focal * .01)
                focal >= profile.physicalMin - tolerance && focal <= profile.physicalMax + tolerance
            }
        }
        return !automatic
    }

    fun reconcile(profiles: List<LensProfile>, hardware: List<HardwareLens>, hardwareDevice: String,
                  aliases: Set<String>): List<LensProfile> {
        if (hardwareDevice.isBlank() || profiles.map { it.id }.distinct().size != profiles.size) return profiles
        val devices = hardware.groupBy { it.id }.filterValues { it.size == 1 }.mapValues { it.value.single() }
        val checked = profiles.map { profile ->
            if (profile.hardwareDevice == hardwareDevice && devices[profile.cameraId]?.let { compatible(profile, it, false) } != true)
                profile.copy(hardwareDevice = "") else profile
        }
        val occupied = checked.filter { it.hardwareDevice == hardwareDevice }.map { it.cameraId }.toSet()
        val candidates = checked.filter { it.hardwareDevice.isBlank() && isCurrent(it, hardwareDevice, aliases) }
            .associate { profile -> profile.id to devices.values.filter { it.id !in occupied && compatible(profile, it, true) } }
        val claims = candidates.values.flatten().groupingBy { it.id }.eachCount()
        return checked.map { profile ->
            // ASVS 2.2.1/2.2.3: imported IDs never bypass local model, focal, facing or uniqueness checks.
            val match = candidates[profile.id]?.singleOrNull()?.takeIf { claims[it.id] == 1 }
            if (match == null) profile else profile.copy(cameraId = match.id, hardwareDevice = hardwareDevice,
                hardwareModel = hardwareDevice, facing = match.facing)
        }
    }

    fun bind(profiles: List<LensProfile>, profileId: String, cameraId: String,
             hardware: List<HardwareLens>, hardwareDevice: String): List<LensProfile> {
        val profile = profiles.single { it.id == profileId }
        val device = hardware.single { it.id == cameraId }
        require(hardwareDevice.isNotBlank() && hardwareDevice.length <= 512 && hardwareDevice.none(Char::isISOControl))
        require(compatible(profile, device, false))
        require(profiles.none { it.id != profileId && it.boundTo(hardwareDevice, cameraId) })
        return profiles.map { if (it.id != profileId) it else it.copy(cameraId = cameraId,
            hardwareDevice = hardwareDevice, hardwareModel = hardwareDevice, facing = device.facing) }
    }

    fun associated(profiles: List<LensProfile>, hardwareDevice: String, cameraId: String) =
        profiles.filter { it.boundTo(hardwareDevice, cameraId) }
    fun unconfigured(cameraIds: List<String>, profiles: List<LensProfile>, hardwareDevice: String) =
        cameraIds.distinct().filter { associated(profiles, hardwareDevice, it).isEmpty() }
    fun hasDuplicates(profiles: List<LensProfile>): Boolean {
        val bound = profiles.filter { it.cameraId.isNotBlank() && it.hardwareDevice.isNotBlank() }
        return bound.map { it.hardwareDevice to it.cameraId }.distinct().size != bound.size
    }
}
