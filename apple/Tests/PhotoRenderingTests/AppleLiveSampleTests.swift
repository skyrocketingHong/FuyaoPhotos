import Testing
import Foundation
@testable import PhotoRenderingCore

/// Regression for the device failure "invalid live photo resource: Invalid image
/// metadata": merging the style MakerNote into a native Apple note used to fail on
/// extended TIFF entry types and discard the tag 17 pairing identifier. Runs against
/// the git-ignored sample docs/samples/apple-airdrop-full-IMG_8565/ when present.
struct AppleLiveSampleTests {
    private static func sample(_ name: String) -> URL? {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("docs/samples/apple-airdrop-full-IMG_8565/\(name)")
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }

    @Test func styleMergeKeepsTheLivePairingIdentifier() async throws {
        guard let source = Self.sample("IMG_8565.HEIC"), let movie = Self.sample("IMG_8565.MOV") else {
            Issue.record("sample missing; skipping live sample regression")
            return
        }
        let container = try HeifContainer.load(fileURL: source)
        let exifItem = try #require(container.items.first { $0.type == "Exif" })
        let payload = try container.payload(of: exifItem.id)
        let app1 = try #require(AppleStyleMetadata.exifApp1Payload(payload))

        // The native capture carries the photographic styles stack; injection refuses it.
        let coverage = StyleInjection.stylesCoverage(in: source)
        #expect(coverage.photographic)

        let movieID = try await LivePhotoMovie.contentIdentifier(movie)
        let merged = try AppleStyleMetadata.appleNoteWithStyle(exif: app1, styleIdentifier: "00112233-4455-6677-8899-aabbccddeeff")
        let tags = AppleStyleMetadata.makerNoteTags(exif: merged)
        #expect(tags.contains(17), "pairing tag must survive the merge")
        #expect(tags.contains(43) && tags.contains(84), "style tags must be present")

        let idBytes = Array(movieID.uppercased().utf8)
        var pairingPreserved = false
        for start in 0...(max(0, merged.count - idBytes.count))
        where Array(merged[start..<start + idBytes.count]) == idBytes {
            pairingPreserved = true
            break
        }
        #expect(pairingPreserved, "pairing identifier payload lost during the merge")
    }
}
