import Testing
import Foundation
import CoreImage
import ImageIO
@testable import PhotoRenderingCore

struct MetadataImageWriterTests {
    @Test(.enabled(if: ProcessInfo.processInfo.environment["FUYAO_LIVE_SAMPLE"] != nil))
    func metadataChangesPreserveLivePhotoPairing() async throws {
        let folder = URL(fileURLWithPath: try #require(ProcessInfo.processInfo.environment["FUYAO_LIVE_SAMPLE"]))
        let source = folder.appendingPathComponent("IMG_8565.HEIC")
        let output = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".heic")
        defer { try? FileManager.default.removeItem(at: output) }
        try MetadataImageWriter.write(source, to: output, options: CardSaveOptions(keepExif: false, keepLocation: false, keepCaptureTime: false))
        try await LivePhotoPair.validate(photo: output, movie: folder.appendingPathComponent("IMG_8565.MOV"))
    }
    @Test(.enabled(if: ProcessInfo.processInfo.environment["FUYAO_HDR_SAMPLE"] != nil))
    func nativeHEICKeepsAuxiliaryImagesAndStyles() throws {
        let source = URL(fileURLWithPath: try #require(ProcessInfo.processInfo.environment["FUYAO_HDR_SAMPLE"]))
        let output = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".heic")
        defer { try? FileManager.default.removeItem(at: output) }
        try MetadataImageWriter.write(source, to: output, options: CardSaveOptions(keepExif: false, keepLocation: false, keepCaptureTime: false))
    }
    @Test func metadataEditDoesNotRenderOrChangePixels() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let source = folder.appendingPathComponent("source.jpg")
        let output = folder.appendingPathComponent("edited.jpg")
        let input = CIImage(color: CIColor(red: 0.3, green: 0.4, blue: 0.6)).cropped(to: CGRect(x: 0, y: 0, width: 64, height: 64))
            .settingProperties([kCGImagePropertyExifDictionary as String: [kCGImagePropertyExifFNumber as String: 2.8,
                kCGImagePropertyExifDateTimeOriginal as String: "2024:04:01 12:00:00"],
                kCGImagePropertyGPSDictionary as String: [kCGImagePropertyGPSLatitude as String: 20,
                    kCGImagePropertyGPSLatitudeRef as String: "N"]])
        try CIContext().writeJPEGRepresentation(of: input, to: source, colorSpace: CGColorSpace(name: CGColorSpace.sRGB)!)
        let originalBytes = try Data(contentsOf: source)
        try MetadataImageWriter.write(source, to: output, options: CardSaveOptions(keepExif: false, keepLocation: false, keepCaptureTime: false))
        let original = try #require(CGImageSourceCreateWithURL(source as CFURL, nil))
        let result = try #require(CGImageSourceCreateWithURL(output as CFURL, nil))
        let before = try #require(CGImageSourceCreateImageAtIndex(original, 0, nil)?.dataProvider?.data)
        let after = try #require(CGImageSourceCreateImageAtIndex(result, 0, nil)?.dataProvider?.data)
        #expect(before as Data == after as Data)
        #expect(try Data(contentsOf: source) == originalBytes)
        let properties = try #require(CGImageSourceCopyPropertiesAtIndex(result, 0, nil) as? [String: Any])
        let exif = properties[kCGImagePropertyExifDictionary as String] as? [String: Any] ?? [:]
        #expect(exif[kCGImagePropertyExifFNumber as String] == nil)
        #expect(exif[kCGImagePropertyExifDateTimeOriginal as String] == nil)
        #expect(properties[kCGImagePropertyGPSDictionary as String] == nil)
    }
}
