import Foundation
import Observation

@MainActor @Observable final class LensWorkspaceDraft {
    var initial: [LensProfile]
    var profiles: [LensProfile]
    var editor: LensEditingSession?
    var hasPendingChanges: Bool { profiles != initial || editor?.hasChanges == true }

    init(profiles: [LensProfile]) { self.initial = profiles; self.profiles = profiles }

    func importConfiguration(_ file: LensProfileFile) throws {
        let merged = try file.merging(into: profiles)
        profiles = merged
        editor = nil
    }

    func apply(_ value: LensProfile) {
        var value = value
        if let index = profiles.firstIndex(where: { $0.id == value.id }) {
            if !profiles[index].acceptsExif(value.exifModel) {
                value.hardwareDevice = nil
                value.hardwareModel = nil
            }
            profiles[index] = value
        } else { profiles.append(value) }
        // The portable format has one product name for every normalized EXIF model.
        profiles = profiles.map { profile in
            var profile = profile
            if profile.acceptsExif(value.exifModel) { profile.device = value.device }
            return profile
        }
    }
}
