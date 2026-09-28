import Testing
import Foundation
import CoreImage
import ImageIO
import UniformTypeIdentifiers
@testable import PhotoRenderingCore

struct LensNameTests {
    @Test func lensNameDropsDevicePrefix() {
        #expect(CardImageProcessor.displayLensName("iPhone 16 Pro back camera 6.86mm f/1.78", device: "iPhone 16 Pro")
                == "back camera 6.86mm f/1.78")
        #expect(CardImageProcessor.displayLensName("iPhone 16 Pro Max front camera 4.25mm f/1.9", device: "iPhone 16 Pro Max")
                == "front camera 4.25mm f/1.9")
        #expect(CardImageProcessor.displayLensName("iPhone 16 Pro", device: "iPhone 16 Pro") == "")
    }

    @Test func lensNameKeepsUnrelatedText() {
        #expect(CardImageProcessor.displayLensName("Xiaomi 95mm f/1.9", device: "iPhone 16 Pro") == "Xiaomi 95mm f/1.9")
        #expect(CardImageProcessor.displayLensName("back camera 6.86mm f/1.78", device: "") == "back camera 6.86mm f/1.78")
        // A shorter model must not half-strip a longer one.
        #expect(CardImageProcessor.displayLensName("iPhone 16 Pro Max back camera 6.86mm f/1.78", device: "iPhone 16 Pro")
                == "iPhone 16 Pro Max back camera 6.86mm f/1.78")
    }

    @Test func readShowsLensWithoutDevicePrefix() async throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let input = folder.appendingPathComponent("lens.heic")
        let image = CIImage(color: CIColor(red: 0.2, green: 0.2, blue: 0.2)).cropped(to: CGRect(x: 0, y: 0, width: 64, height: 48))
        let context = CIContext()
        try context.writeHEIFRepresentation(of: image, to: input, format: .RGBA8,
            colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!, options: [:])
        guard let destination = CGImageDestinationCreateWithURL(input as CFURL, UTType.heic.identifier as CFString, 1, nil) else {
            throw CardError.invalidImage
        }
        let cgImage = try #require(context.createCGImage(image, from: image.extent))
        let properties: [String: Any] = [
            kCGImagePropertyTIFFDictionary as String: [kCGImagePropertyTIFFModel as String: "iPhone 16 Pro"],
            kCGImagePropertyExifDictionary as String: [kCGImagePropertyExifLensModel as String: "iPhone 16 Pro back camera 6.86mm f/1.78"],
        ]
        CGImageDestinationAddImage(destination, cgImage, properties as CFDictionary)
        try #require(CGImageDestinationFinalize(destination))
        let metadata = try await CardImageProcessor.shared.read(input, author: "Author")
        #expect(metadata.card[.device] == "iPhone 16 Pro")
        #expect(metadata.card[.camera] == "back camera 6.86mm f/1.78")
    }
}
