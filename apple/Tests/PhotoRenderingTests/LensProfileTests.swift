import Foundation
import CoreImage
import ImageIO
import Testing
@testable import PhotoRenderingCore

struct LensProfileTests {
    private func profile() -> LensProfile {
        LensProfile(device: "Camera Product", exifModel: "Camera Model", name: "Wide",
                    equivalentMin: 24, equivalentMax: 24, physicalMin: 6, physicalMax: 6,
                    zoomMin: 1, zoomMax: 1, digitalZoomMax: 4)
    }

    @Test func validatesCalibrationSeparatelyFromDigitalCoverage() {
        var lens = profile()
        #expect(lens.isValid)
        lens.zoomMax = 4
        #expect(!lens.isValid)
        lens.zoomMax = 1
        lens.digitalZoomMax = .infinity
        #expect(!lens.isValid)
        lens.digitalZoomMax = nil
        lens.physicalMin = nil
        #expect(!lens.isValid)
    }

    @Test func physicalEvidenceIdentifiesDigitalCropsWithoutClamping() throws {
        let lens = profile()
        let match = try #require(LensProfileResolver.resolve(model: "  camera   model ", lens: "", equivalent: 72,
                                                            physical: 6, profiles: [lens]))
        #expect(match.profile.id == lens.id)
        #expect(match.profile.zoom(for: match.equivalent) == 3)
        #expect(LensProfileResolver.resolve(model: "Other", lens: "", equivalent: 24,
                                           physical: 6, profiles: [lens]) == nil)
    }

    @Test func overlappingProfilesStayUnknown() {
        let first = profile()
        var second = first
        second.id = UUID()
        #expect(LensProfileResolver.resolve(model: first.exifModel, lens: "", equivalent: 24,
                                           physical: 6, profiles: [first, second]) == nil)
        #expect(LensProfileResolver.resolve(model: first.exifModel, lens: "", equivalent: 24,
                                           physical: nil, profiles: [first, second]) == nil)
    }

    @Test func digitalCoverageHasABoundary() {
        let lens = profile()
        #expect(LensProfileResolver.resolve(model: lens.exifModel, lens: "", equivalent: 72,
                                           physical: nil, profiles: [lens]) != nil)
        #expect(LensProfileResolver.resolve(model: lens.exifModel, lens: "", equivalent: 120,
                                           physical: nil, profiles: [lens]) == nil)
        #expect(LensProfileResolver.explicitZoom(in: "Camera (2.5X)") == 2.5)
        #expect(LensProfileResolver.explicitZoom(in: "24mm") == nil)
    }

    @Test @MainActor func savesOnlyValidatedProfiles() throws {
        let suite = "LensProfileTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = LensProfileStore(defaults: defaults)
        let lens = profile()
        try store.save([lens])
        var draft = store.profiles
        draft[0].device = "Changed draft"
        #expect(LensProfileStore(defaults: defaults).profiles == [lens])
        draft[0].equivalentMin = .nan
        #expect(throws: (any Error).self) { try store.save(draft) }
        #expect(store.profiles == [lens])
        #expect(LensProfileStore(defaults: defaults).profiles == [lens])
    }

    @Test func importUsesProfilesWithoutChangingOriginalExif() async throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("LensProfile-\(UUID().uuidString).jpg")
        defer { try? FileManager.default.removeItem(at: url) }
        let lens = profile()
        let image = CIImage(color: .gray).cropped(to: CGRect(x: 0, y: 0, width: 32, height: 24))
            .settingProperties([
                kCGImagePropertyTIFFDictionary as String: [kCGImagePropertyTIFFModel as String: lens.exifModel],
                kCGImagePropertyExifDictionary as String: [kCGImagePropertyExifFocalLength as String: 6,
                                                          kCGImagePropertyExifFocalLenIn35mmFilm as String: 72]
            ])
        try CIContext().writeJPEGRepresentation(of: image, to: url, colorSpace: CGColorSpace(name: CGColorSpace.sRGB)!)
        let original = try Data(contentsOf: url)
        let resolved = try await CardImageProcessor.shared.read(url, author: "", profiles: [lens])
        #expect(resolved.card[.device] == lens.device)
        #expect(resolved.card[.camera] == lens.name)
        #expect(resolved.card[.focalLength] == "72 MM (3X)")
        let raw = try await CardImageProcessor.shared.read(url, author: "")
        #expect(raw.card[.device] == lens.exifModel)
        #expect(raw.card[.camera].isEmpty)
        #expect(raw.card[.focalLength] == "72 MM")
        #expect(try Data(contentsOf: url) == original)
    }
}
