import Foundation
import CoreImage
import ImageIO
import UniformTypeIdentifiers
import Testing
@testable import PhotoRenderingCore

struct CardImageSizeTests {
    @Test @MainActor func defaultPreferenceAndRestorationKeepSourcePixelsSeparate() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        var sourceCard = PhotoCard()
        sourceCard[.imageSize] = "5.9MP"; sourceCard[.camera] = "Matched telephoto"
        let metadata = CardPhotoMetadata(card: sourceCard, width: 2105, height: 2796, latitude: nil, longitude: nil,
            hdr: true, hasPortraitData: false, kind: .ultraHDRJPEG, fileSize: 0, lensMegapixels: 200)
        let source = folder.appendingPathComponent("source.jpg")
        let document = CardDocument(resources: PhotoSourceResources(image: source, movie: nil,
            originalName: "source.jpg", assetIdentifier: nil), metadata: metadata, preferLensPixelCount: true)
        #expect(document.card[.imageSize] == "200MP")
        #expect(document.metadata.card[.imageSize] == "5.9MP")
        document.useFileImageSize()
        #expect(document.card[.imageSize] == "5.9MP")
        document.restoreField(.imageSize, preferLensPixelCount: true)
        #expect(document.card[.imageSize] == "200MP")
        document.card[.camera] = "Manual label"
        document.card.style.opacity = 0.4
        document.restoreInformation(preferLensPixelCount: false)
        for field in CardField.allCases { #expect(document.card[field] == sourceCard[field]) }
        #expect(document.card.style.opacity == 0.4)
        var unavailable = metadata
        unavailable.lensMegapixels = nil
        document.applyMetadataUpdate(source: source, metadata: unavailable)
        document.restoreField(.imageSize, preferLensPixelCount: true)
        #expect(document.card[.imageSize] == "5.9MP")
    }

    @Test @MainActor func croppedPhotoCanUseOfficialLensPixelsWithoutChangingImageOrDefaults() async throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let source = folder.appendingPathComponent("cropped.jpg")
        try writePhoto(source)
        let bytes = try Data(contentsOf: source)
        let profiles = try LensProfileFile.decode(Data(contentsOf: LensProfileFileTests.root
            .appendingPathComponent("shared/fixtures/lenses-v1-with-pixels.json"))).profiles()
        let metadata = try await CardImageProcessor.shared.read(source, author: "Photographer", profiles: profiles)
        #expect(metadata.width == 1000 && metadata.height == 800)
        #expect(metadata.card[.imageSize] == "0.8MP")
        #expect(metadata.lensMegapixels == 50.25 && metadata.lensImageSize == "50.25MP")
        let document = CardDocument(resources: PhotoSourceResources(image: source, movie: nil,
            originalName: "cropped.jpg", assetIdentifier: nil), metadata: metadata)
        document.card[.camera] = "Manual lens label"
        document.card[.device] = "Manual device label"
        let manuallyNamed = document.card
        document.useLensImageSize()
        var expected = manuallyNamed
        expected[.imageSize] = "50.25MP"
        #expect(document.card == expected)
        #expect(document.defaultCard[.imageSize] == "0.8MP")
        #expect(document.metadata.width == 1000 && document.metadata.height == 800)
        let preview = try await CardImageProcessor.shared.preview(source, card: document.card, hdr: false, maxDimension: 1000)
        #expect(preview.width == 1000 && preview.height == 800)
        document.card[.imageSize] = "Custom size"
        document.useFileImageSize()
        #expect(document.card == manuallyNamed)
        #expect(try Data(contentsOf: source) == bytes)
    }

    @Test func missingAndAmbiguousLensEvidenceNeverProvidesOfficialPixels() async throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let source = folder.appendingPathComponent("source.jpg")
        try writePhoto(source)
        let file = try LensProfileFile.decode(Data(contentsOf: LensProfileFileTests.root
            .appendingPathComponent("shared/fixtures/lenses-v1-with-pixels.json")))
        let lens = try #require(file.profiles().first)
        var unconfigured = lens
        unconfigured.originalMegapixels = nil
        var overlapping = lens
        overlapping.id = UUID()
        for profiles in [[], [unconfigured], [lens, overlapping]] {
            let metadata = try await CardImageProcessor.shared.read(source, author: "", profiles: profiles)
            #expect(metadata.lensMegapixels == nil && metadata.lensImageSize == nil)
            #expect(metadata.card[.imageSize] == "0.8MP")
        }
    }

    @Test @MainActor func unavailableLensPixelsKeepManualText() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let metadata = CardPhotoMetadata(card: PhotoCard(), width: 1000, height: 800, latitude: nil, longitude: nil,
            hdr: false, hasPortraitData: false, kind: .stillJPEG, fileSize: 0)
        let document = CardDocument(resources: PhotoSourceResources(image: folder.appendingPathComponent("source.jpg"),
            movie: nil, originalName: "source.jpg", assetIdentifier: nil), metadata: metadata)
        document.card[.imageSize] = "Handwritten size"
        document.useLensImageSize()
        #expect(document.card[.imageSize] == "Handwritten size")
    }

    private func writePhoto(_ url: URL) throws {
        let image = CIImage(color: .gray).cropped(to: CGRect(x: 0, y: 0, width: 1000, height: 800))
        let bitmap = try #require(CIContext().createCGImage(image, from: image.extent))
        let writer = try #require(CGImageDestinationCreateWithURL(url as CFURL, UTType.jpeg.identifier as CFString, 1, nil))
        CGImageDestinationAddImage(writer, bitmap, [
            kCGImagePropertyTIFFDictionary: [kCGImagePropertyTIFFModel: "Example Model"],
            kCGImagePropertyExifDictionary: [kCGImagePropertyExifFocalLength: 6.75,
                kCGImagePropertyExifFocalLenIn35mmFilm: 48]
        ] as CFDictionary)
        #expect(CGImageDestinationFinalize(writer))
    }
}
