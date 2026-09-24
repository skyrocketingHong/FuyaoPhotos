import Testing
import Foundation
import CoreImage
import ImageIO
@testable import PhotoRenderingCore

struct AuxiliaryDataTests {
    @Test(arguments: [CardExportFormat.jpeg, .heic])
    func portraitMatteSurvivesExport(_ format: CardExportFormat) async throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let source = folder.appendingPathComponent("source.heic")
        let target = folder.appendingPathComponent("output.\(format.fileExtension)")
        let image = CIImage(color: CIColor(red: 0.3, green: 0.5, blue: 0.7)).cropped(to: CGRect(x: 0, y: 0, width: 256, height: 192))
        let matte = CIImage(color: .white).cropped(to: CGRect(x: 0, y: 0, width: 64, height: 48))
        try CIContext().writeHEIFRepresentation(of: image, to: source, format: .RGBA8,
            colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!, options: [.portraitEffectsMatteImage: matte])
        let original = try #require(CGImageSourceCreateWithURL(source as CFURL, nil))
        #expect(CGImageSourceCopyAuxiliaryDataInfoAtIndex(original, 0, kCGImageAuxiliaryDataTypePortraitEffectsMatte) != nil)
        var options = CardSaveOptions(); options.format = format
        var card = PhotoCard(); card[.device] = "Camera"
        try await CardImageProcessor.shared.export(source, card: card, options: options, hdr: false, to: target, live: false)
        let result = try #require(CGImageSourceCreateWithURL(target as CFURL, nil))
        #expect(CGImageSourceCopyAuxiliaryDataInfoAtIndex(result, 0, kCGImageAuxiliaryDataTypePortraitEffectsMatte) != nil)
    }
}
