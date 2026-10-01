import Foundation
import Testing
@testable import PhotoRenderingCore

struct LensBindingsTests {
    @Test @MainActor func editingSessionRetainsBindingsAndTracksPendingInput() throws {
        var profile = wide
        profile.hardwareDevice = "local-model"; profile.hardwareModel = "local-model"; profile.stylePrefix = "Example"
        let session = LensEditingSession(draft: LensProfileDraft(profile: profile))
        let workspace = LensWorkspaceDraft(profiles: [profile])
        workspace.editor = session
        #expect(!session.hasChanges)
        session.draft.name = "Edited lens"
        let edited = try #require(session.draft.profile)
        #expect(session.hasChanges && edited.id == profile.id)
        #expect(workspace.hasPendingChanges && workspace.profiles == [profile])
        #expect(edited.cameraID == profile.cameraID && edited.hardwareDevice == profile.hardwareDevice && edited.stylePrefix == profile.stylePrefix)
        session.draft.name = profile.name
        #expect(!session.hasChanges)
        #expect(!workspace.hasPendingChanges)
        session.draft.equivalentMin = "invalid"
        #expect(session.hasChanges && session.draft.profile == nil)
        #expect(session.initial.profile?.equivalentMin == profile.equivalentMin)
    }
    private var wide: LensProfile { LensProfile(device: "Example Phone", exifModel: "RAW MODEL", name: "Wide", facing: .back,
        equivalentMin: 24, equivalentMax: 24, cameraID: "wrong-id") }
    private var hardware: [HardwareLens] { [HardwareLens(id: "wide", name: "Wide", facing: .back, equivalentFocal: 24),
        HardwareLens(id: "tele", name: "Telephoto", facing: .back, equivalentFocal: 120)] }
    private func match(_ profiles: [LensProfile], hardware: [HardwareLens]? = nil) -> [LensProfile] {
        LensBindings.reconcile(profiles, hardware: hardware ?? self.hardware, hardwareDevice: "local-model", aliases: ["RAW MODEL"])
    }

    @Test func importedIDsDoNotChooseTheHardware() {
        for hint in [nil, "wrong-id", "tele", "wide"] as [String?] {
            var profile = wide; profile.cameraID = hint
            let result = match([profile])[0]
            #expect(result.cameraID == "wide" && result.hardwareDevice == "local-model")
        }
        var profile = wide; profile.equivalentMin = 70; profile.equivalentMax = 70; profile.cameraID = "wide"
        #expect(match([profile])[0].hardwareDevice == nil)
    }

    @Test func ambiguousOtherDeviceAndFrontBackConflictsStayUnbound() {
        var other = wide; other.device = "Other"; other.exifModel = "OTHER"
        #expect(match([other])[0].hardwareDevice == nil)
        other = wide; other.hardwareModel = "another-model"
        #expect(match([other])[0].hardwareDevice == nil)
        other = wide; other.facing = .front
        #expect(match([other])[0].hardwareDevice == nil)
        #expect(match([wide,wide]).allSatisfy { $0.hardwareDevice == nil })
        let profile = wide
        #expect(match([profile,profile]) == [profile,profile])
        #expect(match([wide], hardware: hardware + [HardwareLens(id: "duplicate", name: "Wide 2", facing: .back, equivalentFocal: 24)])[0].hardwareDevice == nil)
    }

    @Test func manualLinksValidateEvidenceAndPreventDuplicateBindings() throws {
        let profile = wide
        #expect(throws: LensBindings.Failure.self) {
            try LensBindings.bind([profile], profileID: profile.id, cameraID: "tele", hardware: hardware, hardwareDevice: "local-model")
        }
        let linked = try LensBindings.bind([profile], profileID: profile.id, cameraID: "wide", hardware: hardware, hardwareDevice: "local-model")[0]
        let other = wide
        #expect(throws: LensBindings.Failure.self) {
            try LensBindings.bind([linked,other], profileID: other.id, cameraID: "wide", hardware: hardware, hardwareDevice: "local-model")
        }
        #expect(match([linked], hardware: [])[0].hardwareDevice == nil)
    }

    @Test func currentDeviceComesBeforeAlphabeticalOtherDevices() {
        var beta = wide; beta.device = "Beta"; beta.exifModel = "B"
        var alpha = wide; alpha.device = "alpha"; alpha.exifModel = "A"
        var current = wide; current.device = "Zulu"
        let groups = LensBindings.groups([beta,current,alpha], hardwareDevice: "local-model", aliases: ["RAW MODEL"])
        #expect(groups.map(\.device) == ["Zulu","alpha","Beta"])
        #expect(groups.map(\.isCurrent) == [true,false,false])
    }

    @Test func oldPersistedProfilesStillDecode() throws {
        let original = wide
        let encoded = try JSONEncoder().encode(original)
        var value = try #require(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
        for key in ["cameraID", "hardwareDevice", "hardwareModel"] { value.removeValue(forKey: key) }
        let restored = try JSONDecoder().decode(LensProfile.self, from: JSONSerialization.data(withJSONObject: value))
        #expect(restored.isValid && restored.cameraID == nil && restored.hardwareDevice == nil && restored.hardwareModel == nil)
    }
}
