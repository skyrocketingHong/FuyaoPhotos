import Testing
import Foundation
import ImageIO
import CoreImage
@testable import PhotoRenderingCore

struct NativeCardExportTests {
    private static var source: URL {
        URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("docs/samples/apple-native-styles-IMG_0311/IMG_0311.HEIC")
    }

    @Test(.enabled(if: FileManager.default.fileExists(atPath: source.path)),
          arguments: ["apple-native-styles-IMG_0311/IMG_0311.HEIC", "apple-airdrop-full-IMG_8565/IMG_8565.HEIC"])
    func cardKeepsNativeStylesDepthAndTheirRelationships(_ sample: String) async throws {
        let source = Self.source.deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent(sample)
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let output = directory.appendingPathComponent("card.heic")
        var card = PhotoCard()
        card[.device] = "iPhone 18 Pro Max"
        card[.camera] = "Fusion Main"
        card[.photographicStyle] = "Standard"
        let metadata = try await CardImageProcessor.shared.read(source, author: "")
        #expect(metadata.nativeEditingData && metadata.hdr)
        try await CardImageProcessor.shared.export(source, card: card,
            options: CardSaveOptions(format: .heic, keepLocation: true), hdr: metadata.hdr, to: output, live: true)
        let original = try HeifContainer.load(fileURL: source)
        let result = try HeifContainer.load(fileURL: output)
        #expect(result.primary == original.primary)
        for item in original.items where ["Exif", "mime", "uri "].contains(item.type)
            || original.auxCURN(of: item.id).map({ !$0.contains("hdrgainmap") }) == true {
            #expect(try result.payload(of: item.id) == original.payload(of: item.id))
        }
        for reference in original.references where ["auxl", "cdsc"].contains(reference.type) {
            #expect(result.references.contains(reference))
        }
        let decoded = try await CardImageProcessor.shared.read(output, author: "")
        #expect(decoded.width == metadata.width && decoded.height == metadata.height)
        #expect(decoded.hdr && decoded.hasPortraitData && decoded.nativeEditingData)
        #expect(decoded.card[.photographicStyle] == metadata.card[.photographicStyle])
        #expect((CIImage(contentsOf: output, options: [.expandToHDR: true])?.contentHeadroom ?? 1) > 1)
        let before = try #require(CIImage(contentsOf: source, options: [.applyOrientationProperty: true, .expandToHDR: false, .toneMapHDRtoSDR: true]))
        let after = try #require(CIImage(contentsOf: output, options: [.applyOrientationProperty: true, .expandToHDR: false, .toneMapHDRtoSDR: true]))
        let layout = try CardLayout(size: before.extent.size, card: card)
        let outside = CGRect(x: before.extent.width - 100, y: before.extent.height / 2, width: 60, height: 60)
        #expect(try difference(before, after, in: outside) < 0.025)
        #expect(try difference(before, after, in: layout.rect.insetBy(dx: 30, dy: 30)) > 0.025)
    }

    @Test func cardKeepsInjectedStylesWithISOGainMap() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let source = directory.appendingPathComponent("source.heic"), styled = directory.appendingPathComponent("styled.heic")
        let output = directory.appendingPathComponent("card.heic")
        let base = CIImage(color: CIColor(red: 0.4, green: 0.5, blue: 0.6)).cropped(to: CGRect(x: 0, y: 0, width: 1024, height: 768))
        let hdr = base.applyingFilter("CIExposureAdjust", parameters: [kCIInputEVKey: 2]).settingContentHeadroom(4)
        try CIContext().writeHEIFRepresentation(of: base, to: source, format: .RGBA8,
            colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!, options: [.hdrImage: hdr])
        try StyleInjection.inject(source: source, kind: .heicWithAuxiliaryData, hdr: true,
            addPhotographic: true, addTexture: true, grainSeedName: "generated", destination: styled)
        var card = PhotoCard(); card[.device] = "Camera"
        try await CardImageProcessor.shared.export(styled, card: card,
            options: CardSaveOptions(format: .heic, keepLocation: true), hdr: true, to: output, live: false)
        let before = try HeifContainer.load(fileURL: styled), after = try HeifContainer.load(fileURL: output)
        #expect(after.stylesCoverage.texture && after.stylesCoverage.photographic)
        for item in before.items where item.type == "uri " || before.auxCURN(of: item.id)?.contains("tag:apple.com") == true {
            #expect(try before.payload(of: item.id) == after.payload(of: item.id))
        }
        let decoded = try #require(CIImage(contentsOf: output, options: [.expandToHDR: true]))
        #expect(decoded.contentHeadroom > 1)
    }

    @Test(.enabled(if: FileManager.default.fileExists(atPath: source.path)))
    func removingCaptureInfoKeepsTheNativeEditingResources() async throws {
        let output = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".heic")
        defer { try? FileManager.default.removeItem(at: output) }
        var card = PhotoCard(); card[.device] = "Camera"
        var options = CardSaveOptions(format: .heic)
        options.keepExif = false; options.keepCaptureTime = false; options.keepLocation = false
        try await CardImageProcessor.shared.export(Self.source, card: card, options: options, hdr: true, to: output, live: true)
        let before = try HeifContainer.load(fileURL: Self.source), after = try HeifContainer.load(fileURL: output)
        for item in before.items where item.type == "uri " || before.auxCURN(of: item.id).map({ !$0.contains("hdrgainmap") }) == true {
            let resource = before.auxCURN(of: item.id) ?? before.contentType(of: item) ?? item.type
            let retained = try #require(after.items.first {
                (after.auxCURN(of: $0.id) ?? after.contentType(of: $0) ?? $0.type) == resource
            }, "Missing editing resource: \(resource)")
            #expect(try after.payload(of: retained.id) == before.payload(of: item.id), "Changed editing resource: \(resource)")
        }
        let reader = try #require(CGImageSourceCreateWithURL(output as CFURL, nil))
        let properties = try #require(CGImageSourceCopyPropertiesAtIndex(reader, 0, nil) as? [String: Any])
        #expect(properties[kCGImagePropertyGPSDictionary as String] == nil)
        let exif = properties[kCGImagePropertyExifDictionary as String] as? [String: Any] ?? [:]
        #expect(exif[kCGImagePropertyExifFNumber as String] == nil)
        #expect(exif[kCGImagePropertyExifDateTimeOriginal as String] == nil)
    }

    private func difference(_ before: CIImage, _ after: CIImage, in rect: CGRect) throws -> Double {
        let image = after.applyingFilter("CIDifferenceBlendMode", parameters: [kCIInputBackgroundImageKey: before])
            .applyingFilter("CIAreaAverage", parameters: [kCIInputExtentKey: CIVector(cgRect: rect)])
        var values = [Float](repeating: 0, count: 4)
        values.withUnsafeMutableBytes {
            CIContext().render(image, toBitmap: $0.baseAddress!, rowBytes: 16,
                bounds: CGRect(x: 0, y: 0, width: 1, height: 1), format: .RGBAf,
                colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!)
        }
        return Double(values[0...2].reduce(0, +) / 3)
    }

    @Test(.enabled(if: FileManager.default.fileExists(atPath: source.path)))
    func jpegCannotSilentlyDiscardNativeStyleResources() async throws {
        let output = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".jpg")
        defer { try? FileManager.default.removeItem(at: output) }
        await #expect(throws: CardError.nativeMetadataFormat) {
            try await CardImageProcessor.shared.export(Self.source, card: PhotoCard(),
                options: CardSaveOptions(format: .jpeg), hdr: true, to: output, live: false)
        }
        #expect(!FileManager.default.fileExists(atPath: output.path))
    }
}
