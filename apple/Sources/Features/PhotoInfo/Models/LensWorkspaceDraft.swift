import Foundation
import Observation

@MainActor @Observable final class LensWorkspaceDraft {
    var initial: [LensProfile]
    var profiles: [LensProfile]
    var editor: LensEditingSession?
    var hasPendingChanges: Bool { profiles != initial || editor?.hasChanges == true }

    init(profiles: [LensProfile]) { self.initial = profiles; self.profiles = profiles }
}
