import Testing
import Foundation
@testable import PhotoRenderingCore

struct CardLocationFormattingTests {
    @Test func usesCityAndEnglishCountryOnly() {
        #expect(CardLocationFormatting.place(city: "Hangzhou", country: "中国", countryCode: "CN") == "Hangzhou, China")
        #expect(CardLocationFormatting.place(city: "New York", country: "United States", countryCode: "US") == "New York, United States")
        #expect(CardLocationFormatting.place(city: "Paris", country: "France", countryCode: "FR") == "Paris, France")
    }

    @Test func missingCityDoesNotBecomeAnAdministrativeArea() {
        #expect(CardLocationFormatting.place(city: nil, country: "中国", countryCode: "CN") == "China")
        #expect(CardLocationFormatting.place(city: " ", country: nil, countryCode: nil).isEmpty)
        #expect(CardLocationFormatting.place(city: "Town", country: "Country", countryCode: "ZZ") == "Town, Country")
    }

    @Test func deduplicatesCityStatesAndWhitespace() {
        #expect(CardLocationFormatting.place(city: "Singapore", country: "Singapore", countryCode: "SG") == "Singapore")
        #expect(CardLocationFormatting.place(city: "  Hong   Kong ", country: "Hong Kong", countryCode: nil) == "Hong Kong")
    }

    @Test @MainActor func metadataRefreshKeepsResolvedBaselineAndManualText() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        func metadata(latitude: Double?, longitude: Double?) -> CardPhotoMetadata {
            CardPhotoMetadata(card: PhotoCard(), width: 64, height: 64, latitude: latitude, longitude: longitude,
                hdr: false, hasPortraitData: false, kind: .stillHEIC, fileSize: 0)
        }
        let source = folder.appendingPathComponent("source.heic")
        let document = CardDocument(resources: PhotoSourceResources(image: source, movie: nil,
            originalName: "source.heic", assetIdentifier: nil), metadata: metadata(latitude: 30, longitude: 120))
        document.applyResolvedLocation("Hangzhou, China")
        document.card[.location] = "My own place"
        let revision = document.locationRevision
        document.applyMetadataUpdate(source: source, metadata: metadata(latitude: 30, longitude: 120))
        #expect(document.defaultCard[.location] == "Hangzhou, China")
        #expect(document.card[.location] == "My own place")
        #expect(document.locationRevision > revision)
        document.applyMetadataUpdate(source: source, metadata: metadata(latitude: nil, longitude: nil))
        #expect(document.defaultCard[.location].isEmpty)
        #expect(document.card[.location] == "My own place")
    }
}
