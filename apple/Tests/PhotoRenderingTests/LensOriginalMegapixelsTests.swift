import Foundation
import Testing
@testable import PhotoRenderingCore

struct LensOriginalMegapixelsTests {
    private var profile: LensProfile {
        LensProfile(device: "Example Camera", exifModel: "Example Model", name: "Wide",
                    equivalentMin: 24, equivalentMax: 24, cameraID: "wide",
                    hardwareDevice: "local-model", hardwareModel: "local-model", stylePrefix: "Example")
    }

    @Test(arguments: [50.0, 48.0, 12.2, 1_000.0, 0.000000001, 12.234567891234567])
    func valuesRoundTripThroughLocalAndExchangeCodecs(megapixels: Double) throws {
        var original = profile
        original.originalMegapixels = megapixels
        #expect(original.isValid)
        let local = try JSONDecoder().decode(LensProfile.self, from: JSONEncoder().encode(original))
        #expect(local == original)
        let encoded = try LensProfileFile(profiles: [original]).encoded()
        let file = try LensProfileFile.decode(encoded)
        let restored = try #require(file.profiles().first)
        #expect(restored.originalMegapixels == megapixels)
        #expect(restored.cameraID == original.cameraID && restored.stylePrefix == original.stylePrefix)
        #expect(restored.hardwareDevice == nil && restored.hardwareModel == original.hardwareDevice)
        #expect(restored.id != original.id)
        #expect(!encoded.contains(10) && !encoded.contains(13))
        #expect(try LensProfileFile.decode(file.encoded()) == file)
        let object = try #require(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
        let lenses = try #require(object["lenses"] as? [[String: Any]])
        #expect(lenses.first?["originalMegapixels"] as? Double == megapixels)
    }

    @Test func sharedFixtureAndOldVersionOneRemainCompatible() throws {
        let withPixels = try LensProfileFile.decode(Data(contentsOf: LensProfileFileTests.root
            .appendingPathComponent("shared/fixtures/lenses-v1-with-pixels.json")))
        #expect(withPixels.version == 1)
        #expect(try withPixels.profiles().map(\.originalMegapixels) == [50.25, 48])
        #expect(try LensProfileFile.decode(withPixels.encoded()) == withPixels)
        let legacy = try LensProfileFile.decode(Data(contentsOf: LensProfileFileTests.root
            .appendingPathComponent("shared/fixtures/lenses-v1.json")))
        #expect(try legacy.profiles().allSatisfy { $0.originalMegapixels == nil })
        #expect(try LensProfileFile.decode(legacy.encoded()) == legacy)
        #expect(try !String(decoding: legacy.encoded(), as: UTF8.self).contains("originalMegapixels"))
    }

    @Test func absentAndNullLocalValuesMeanUnconfigured() throws {
        let original = profile
        let bytes = try JSONEncoder().encode(original)
        var object = try #require(JSONSerialization.jsonObject(with: bytes) as? [String: Any])
        #expect(object["originalMegapixels"] == nil)
        #expect(try JSONDecoder().decode(LensProfile.self, from: bytes) == original)
        object["originalMegapixels"] = NSNull()
        let restored = try JSONDecoder().decode(LensProfile.self, from: JSONSerialization.data(withJSONObject: object))
        #expect(restored.originalMegapixels == nil && restored.isValid)
        #expect(try LensProfileFile.decode(exchangeJSON(value: "null")).profiles().first?.originalMegapixels == nil)
    }

    @Test(arguments: ["\"48\"", "true", "0", "-1", "1000.1", "1e309", "\"NaN\"", "[]", "{}"])
    func exchangeRejectsInvalidTypesAndValues(value: String) {
        #expect(throws: LensProfileFileError.self) { try LensProfileFile.decode(exchangeJSON(value: value)) }
    }

    @Test(arguments: [0.0, -1.0, 1_000.1, Double.nan, Double.infinity, -Double.infinity])
    func invalidModelValuesCannotBeExported(value: Double) {
        var invalid = profile
        invalid.originalMegapixels = value
        #expect(!invalid.isValid)
        #expect(throws: LensProfileFileError.self) { try LensProfileFile(profiles: [invalid]).encoded() }
    }

    @Test(arguments: [50.0, 48.0, 12.2, 1_000.0, 0.000000001, 12.234567891234567])
    func draftPreservesValueAndUnrelatedLensFields(megapixels: Double) throws {
        var original = profile
        original.originalMegapixels = megapixels
        var draft = LensProfileDraft(profile: original)
        #expect(draft.validationKey == nil)
        #expect(draft.profile == original)
        draft.name = "Edited lens"
        let edited = try #require(draft.profile)
        #expect(edited.originalMegapixels == megapixels)
        #expect(edited.id == original.id && edited.cameraID == original.cameraID)
        #expect(edited.hardwareDevice == original.hardwareDevice && edited.hardwareModel == original.hardwareModel)
        #expect(edited.stylePrefix == original.stylePrefix)
        draft.originalMegapixels = " \n "
        #expect(draft.validationKey == nil && draft.profile?.originalMegapixels == nil)
    }

    @Test(arguments: ["0", "-1", "1000.1", "NaN", "inf", "Infinity", "1e309", "48 MP", "text"])
    func invalidDraftValuesCannotBeApplied(value: String) {
        var draft = LensProfileDraft(profile: profile)
        draft.originalMegapixels = value
        #expect(draft.validationKey == "lens.validation.originalMegapixels")
        #expect(draft.profile == nil)
    }

    @Test @MainActor func editingSessionTracksPixelChangesAndRestoration() {
        var original = profile
        original.originalMegapixels = 48
        let session = LensEditingSession(draft: LensProfileDraft(profile: original))
        #expect(!session.hasChanges)
        session.draft.originalMegapixels = "50"
        #expect(session.hasChanges && session.draft.profile?.originalMegapixels == 50)
        session.draft.originalMegapixels = session.initial.originalMegapixels
        #expect(!session.hasChanges)
    }

    @Test @MainActor func storeLoadsLegacyProfilesAndRejectsInvalidPixelUpdates() throws {
        let suite = "LensOriginalMegapixelsTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let original = profile
        defaults.set(try JSONEncoder().encode([original]), forKey: "card.lensProfiles")
        let store = LensProfileStore(defaults: defaults)
        #expect(store.profiles == [original])
        var configured = original
        configured.originalMegapixels = 12.2
        try store.save([configured])
        #expect(LensProfileStore(defaults: defaults).profiles == [configured])
        var invalid = configured
        invalid.originalMegapixels = 0
        #expect(throws: (any Error).self) { try store.save([invalid]) }
        #expect(store.profiles == [configured])
        #expect(LensProfileStore(defaults: defaults).profiles == [configured])
    }

    private func exchangeJSON(value: String) -> Data {
        Data("""
        {"format":"fuyaophotos.lenses","version":1,"device":"Example Camera","exifModel":"Example Model","lenses":[{"name":"Wide","facing":"back","equivalentMin":24,"equivalentMax":24,"originalMegapixels":\(value)}]}
        """.utf8)
    }
}
