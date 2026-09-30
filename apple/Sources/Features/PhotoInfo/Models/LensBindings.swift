import Foundation

nonisolated struct HardwareLens: Equatable, Identifiable, Sendable {
    let id: String
    let name: String
    let facing: LensProfile.Facing
    let equivalentFocal: Double?
}

nonisolated enum LensBindings {
    enum Failure: Error { case invalid }
    struct DeviceGroup: Identifiable, Sendable {
        let id: String
        let profiles: [LensProfile]
        let isCurrent: Bool
        var device: String { profiles[0].device }
        var exifModel: String { profiles[0].exifModel }
    }

    static func isCurrent(_ profile: LensProfile, hardwareDevice: String, aliases: Set<String>) -> Bool {
        if !hardwareDevice.isEmpty && profile.hardwareDevice == hardwareDevice { return true }
        if let model = profile.hardwareModel, !model.isEmpty { return model == hardwareDevice }
        let names = Set(aliases.filter { !$0.isEmpty }.map(LensProfile.normalize))
        return names.contains(LensProfile.normalize(profile.device)) || names.contains(LensProfile.normalize(profile.exifModel))
    }

    static func groups(_ profiles: [LensProfile], hardwareDevice: String, aliases: Set<String>) -> [DeviceGroup] {
        Dictionary(grouping: profiles) { LensProfile.normalize($0.exifModel) }.map { model, lenses in
            DeviceGroup(id: model, profiles: lenses, isCurrent: lenses.contains { isCurrent($0, hardwareDevice: hardwareDevice, aliases: aliases) })
        }.sorted {
            if $0.isCurrent != $1.isCurrent { return $0.isCurrent }
            let left = LensProfile.normalize($0.device), right = LensProfile.normalize($1.device)
            return left == right ? $0.id < $1.id : left < right
        }
    }

    static func compatible(_ profile: LensProfile, with hardware: HardwareLens, automatic: Bool) -> Bool {
        guard profile.isValid, !hardware.id.isEmpty, hardware.id.count <= 256,
              hardware.id.unicodeScalars.allSatisfy({ !CharacterSet.controlCharacters.contains($0) }),
              profile.facing == .unspecified || profile.facing == hardware.facing else { return false }
        guard let focal = hardware.equivalentFocal, focal.isFinite, focal > 0 else { return !automatic }
        return focal >= profile.equivalentMin - 1 && focal <= profile.equivalentMax + 1
    }

    static func reconcile(_ profiles: [LensProfile], hardware: [HardwareLens], hardwareDevice: String,
                          aliases: Set<String>) -> [LensProfile] {
        guard !hardwareDevice.isEmpty, Set(profiles.map(\.id)).count == profiles.count else { return profiles }
        let devices = Dictionary(grouping: hardware, by: \.id).compactMapValues { $0.count == 1 ? $0[0] : nil }
        let checked = profiles.map { profile in
            var result = profile
            if profile.hardwareDevice == hardwareDevice,
               profile.cameraID.flatMap({ devices[$0] }).map({ compatible(profile, with: $0, automatic: false) }) != true {
                result.hardwareDevice = nil
            }
            return result
        }
        let occupied = Set(checked.filter { $0.hardwareDevice == hardwareDevice }.compactMap(\.cameraID))
        let candidates = Dictionary(uniqueKeysWithValues: checked.filter {
            $0.hardwareDevice == nil && isCurrent($0, hardwareDevice: hardwareDevice, aliases: aliases)
        }.map { profile in
            (profile.id, devices.values.filter { !occupied.contains($0.id) && compatible(profile, with: $0, automatic: true) })
        })
        let claims = Dictionary(grouping: candidates.values.flatMap { $0 }, by: \.id)
        return checked.map { profile in
            // ASVS 2.2.1/2.2.3: the imported ID cannot choose or disambiguate a scanned lens.
            guard let matches = candidates[profile.id], matches.count == 1, let match = matches.first,
                  claims[match.id]?.count == 1 else { return profile }
            var result = profile
            result.cameraID = match.id; result.hardwareDevice = hardwareDevice; result.hardwareModel = hardwareDevice
            result.facing = match.facing
            return result
        }
    }

    static func bind(_ profiles: [LensProfile], profileID: UUID, cameraID: String,
                     hardware: [HardwareLens], hardwareDevice: String) throws -> [LensProfile] {
        guard !hardwareDevice.isEmpty, hardwareDevice.count <= 512,
              hardwareDevice.unicodeScalars.allSatisfy({ !CharacterSet.controlCharacters.contains($0) }),
              profiles.filter({ $0.id == profileID }).count == 1,
              let profile = profiles.first(where: { $0.id == profileID }),
              hardware.filter({ $0.id == cameraID }).count == 1,
              let device = hardware.first(where: { $0.id == cameraID }), compatible(profile, with: device, automatic: false),
              !profiles.contains(where: { $0.id != profileID && $0.hardwareDevice == hardwareDevice && $0.cameraID == cameraID }) else {
            throw Failure.invalid
        }
        return profiles.map {
            guard $0.id == profileID else { return $0 }
            var result = $0
            result.cameraID = cameraID; result.hardwareDevice = hardwareDevice; result.hardwareModel = hardwareDevice
            result.facing = device.facing
            return result
        }
    }

    static func hasDuplicates(_ profiles: [LensProfile]) -> Bool {
        let keys = profiles.compactMap { profile -> String? in
            guard let device = profile.hardwareDevice, let id = profile.cameraID, !device.isEmpty, !id.isEmpty else { return nil }
            return "\(device)\n\(id)"
        }
        return Set(keys).count != keys.count
    }
}
