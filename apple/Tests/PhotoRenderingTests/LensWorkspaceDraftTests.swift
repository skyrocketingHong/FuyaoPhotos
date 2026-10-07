import Testing
import Foundation
@testable import PhotoRenderingCore

@MainActor struct LensWorkspaceDraftTests {
    @Test func importPreviewLeavesTheDraftUntouchedAndImportDoesNotSaveIt() throws {
        let suite = "LensWorkspaceDraftTests-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let original = LensProfile(device: "Original", exifModel: "Original Model", name: "Wide", equivalentMin: 24, equivalentMax: 24)
        let replacement = LensProfile(device: "New", exifModel: "Original Model", name: "Main", equivalentMin: 28, equivalentMax: 28)
        let store = LensProfileStore(defaults: defaults)
        try store.save([original])
        let workspace = LensWorkspaceDraft(profiles: store.profiles)
        let file = try LensProfileFile(profiles: [replacement])
        _ = try file.profiles()
        #expect(workspace.profiles == [original] && !workspace.hasPendingChanges)
        try workspace.importConfiguration(file)
        #expect(workspace.profiles.first?.device == "New")
        #expect(workspace.hasPendingChanges && workspace.initial == [original])
        #expect(store.profiles == [original])
        try store.save(workspace.profiles)
        workspace.initial = workspace.profiles
        #expect(store.profiles == workspace.profiles && !workspace.hasPendingChanges)
    }

    @Test func rejectedImportKeepsTheListAndPendingEditor() throws {
        let lens = LensProfile(device: "Camera", exifModel: "Camera Model", name: "Wide", equivalentMin: 24, equivalentMax: 24)
        let workspace = LensWorkspaceDraft(profiles: [lens])
        let editor = LensEditingSession(draft: LensProfileDraft(profile: lens))
        editor.draft.name = "Pending name"
        workspace.editor = editor
        var invalid = try LensProfileFile(profiles: [lens])
        invalid.version = 999
        #expect(throws: LensProfileFileError.self) { try workspace.importConfiguration(invalid) }
        #expect(workspace.profiles == [lens] && workspace.initial == [lens])
        #expect(workspace.editor === editor && editor.hasChanges)
    }

    @Test func productRenameKeepsModelExportableWithoutChangingOtherDevices() throws {
        let wide = LensProfile(device: "Camera", exifModel: "Camera Model", name: "Wide", equivalentMin: 24, equivalentMax: 24)
        let tele = LensProfile(device: "Camera", exifModel: "camera model", name: "Tele", equivalentMin: 80, equivalentMax: 80)
        let other = LensProfile(device: "Other", exifModel: "Other Model", name: "Wide", equivalentMin: 24, equivalentMax: 24)
        let workspace = LensWorkspaceDraft(profiles: [wide, tele, other])
        var changed = wide
        changed.device = "Updated Camera"
        workspace.apply(changed)
        #expect(workspace.profiles.map(\.device) == ["Updated Camera", "Updated Camera", "Other"])
        #expect(workspace.initial == [wide, tele, other])
        #expect(workspace.hasPendingChanges)
        let file = try LensProfileFile(profiles: Array(workspace.profiles.prefix(2)))
        #expect(try LensProfileFile.decode(file.encoded()).device == "Updated Camera")
    }

    @Test func movingLensToAnotherModelDropsTheOldHardwareBinding() {
        let lens = LensProfile(device: "Camera", exifModel: "Camera Model", name: "Wide", equivalentMin: 24,
            equivalentMax: 24, cameraID: "lens-id", hardwareDevice: "local-hardware", hardwareModel: "local-hardware")
        let workspace = LensWorkspaceDraft(profiles: [lens])
        var moved = lens
        moved.exifModel = "Other Model"
        workspace.apply(moved)
        #expect(workspace.profiles[0].hardwareDevice == nil)
        #expect(workspace.profiles[0].hardwareModel == nil)
        #expect(workspace.profiles[0].cameraID == lens.cameraID)
        #expect(workspace.initial == [lens])
    }

    @Test func applyingAnUnchangedLensLeavesTheListClean() {
        let lens = LensProfile(device: "Camera", exifModel: "Camera Model", name: "Wide", equivalentMin: 24, equivalentMax: 24)
        let workspace = LensWorkspaceDraft(profiles: [lens])
        workspace.apply(lens)
        #expect(!workspace.hasPendingChanges)
    }
}
