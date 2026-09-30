import Foundation
import Testing
import CoreImage
import ImageIO
import UniformTypeIdentifiers
@testable import PhotoRenderingCore

struct LensProfileFileTests {
    static var root: URL { URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent() }
    @Test func hardwareIDsRoundTripAsUntrustedHints() throws {
        let bytes = try Data(contentsOf: Self.root.appendingPathComponent("shared/fixtures/lenses-v1-with-ids.json"))
        let file = try LensProfileFile.decode(bytes)
        let profiles = try file.profiles()
        #expect(profiles.map(\.cameraID) == ["0", "2"])
        #expect(profiles.allSatisfy { $0.stylePrefix == "Example" })
        #expect(profiles.allSatisfy { $0.hardwareDevice == nil && $0.hardwareModel == "Example|Model" })
        let again = try LensProfileFile.decode(LensProfileFile(profiles: profiles).encoded())
        #expect(again == file)
        let old = try LensProfileFile.decode(Data(contentsOf: Self.root.appendingPathComponent("shared/fixtures/lenses-v1.json")))
        #expect(try old.profiles().allSatisfy { $0.cameraID == nil && $0.hardwareDevice == nil && $0.stylePrefix == nil })
        let text = try #require(String(data: bytes, encoding: .utf8))
        for invalid in [text.replacingOccurrences(of: #""id":"0""#, with: #""id":0"#),
                        text.replacingOccurrences(of: #""id":"0""#, with: #""id":"bad\nID""#)] {
            #expect(throws: LensProfileFileError.self) { try LensProfileFile.decode(Data(invalid.utf8)) }
        }
        let tooLong = text.replacingOccurrences(of: "\"stylePrefix\":\"Example\"", with: "\"stylePrefix\":\"" + String(repeating: "A", count: 65) + "\"")
        #expect(throws: LensProfileFileError.self) { try LensProfileFile.decode(Data(tooLong.utf8)) }
    }
    @Test func deviceFileIsCompactPortableAndReplacesOnlyThatModel() throws {
        let bytes = try Data(contentsOf: Self.root.appendingPathComponent("shared/fixtures/lenses-v1.json"))
        let file = try LensProfileFile.decode(bytes)
        let exported = try file.encoded()
        #expect(!exported.contains(10) && !exported.contains(13))
        #expect(try LensProfileFile.decode(exported) == file)
        let retained = LensProfile(device: "Other", exifModel: "Other", name: "Wide", equivalentMin: 24, equivalentMax: 24)
        let existing = try file.profiles() + [retained]
        let merged = try file.merging(into: existing)
        #expect(merged.count == 3 && merged.contains(retained))
        let match = LensProfileResolver.resolve(model: "Example Model", lens: "Back camera", equivalent: 200, physical: 16.9, profiles: merged)
        #expect(match?.profile.name == "Telephoto" && match?.profile.zoom(for: 200) == 8)
    }
    @Test func invalidVersionsAndRangesDoNotProduceProfiles() throws {
        let original = try String(contentsOf: Self.root.appendingPathComponent("shared/fixtures/lenses-v1.json"), encoding: .utf8)
        for malformed in [original.replacingOccurrences(of: "\"version\":1", with: "\"version\":2"),
                          original.replacingOccurrences(of: "\"equivalentMin\":24", with: "\"equivalentMin\":-24"), original + "extra"] {
            #expect(throws: LensProfileFileError.self) { try LensProfileFile.decode(Data(malformed.utf8)) }
        }
    }
    @Test func importedLensNameOverridesOnlyTheDisplay() async throws {
        let file = try LensProfileFile.decode(Data(contentsOf: Self.root.appendingPathComponent("shared/fixtures/lenses-v1.json")))
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".jpg")
        defer { try? FileManager.default.removeItem(at: url) }
        let image = CIImage(color: .gray).cropped(to: CGRect(x: 0, y: 0, width: 64, height: 48))
        let context = CIContext()
        let bitmap = try #require(context.createCGImage(image, from: image.extent))
        let writer = try #require(CGImageDestinationCreateWithURL(url as CFURL, UTType.jpeg.identifier as CFString, 1, nil))
        CGImageDestinationAddImage(writer, bitmap, [kCGImagePropertyTIFFDictionary: [kCGImagePropertyTIFFModel: "Example Model"],
            kCGImagePropertyExifDictionary: [kCGImagePropertyExifLensModel: "Original lens description", kCGImagePropertyExifFocalLength: 6.75,
                kCGImagePropertyExifFocalLenIn35mmFilm: 48]] as CFDictionary)
        #expect(CGImageDestinationFinalize(writer))
        let before = try Data(contentsOf: url)
        let read = try await CardImageProcessor.shared.read(url, author: "", profiles: file.profiles())
        #expect(read.card[.camera] == "Wide" && read.card[.device] == "Example Camera")
        #expect(read.card[.focalLength] == "48 MM (2X)")
        #expect(try Data(contentsOf: url) == before)
    }
}
