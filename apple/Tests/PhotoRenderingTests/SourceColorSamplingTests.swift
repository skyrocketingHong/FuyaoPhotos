import Testing
import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers
@testable import PhotoColorsCore

struct SourceColorSamplingTests {
    @Test func automaticRetainsLinearSourceEncoding() async throws {
        let space = try #require(CGColorSpace(name: CGColorSpace.linearSRGB))
        let bytes = Data([64, 128, 192, 255])
        let provider = try #require(CGDataProvider(data: bytes as CFData))
        let image = try #require(CGImage(width: 1, height: 1, bitsPerComponent: 8, bitsPerPixel: 32,
            bytesPerRow: 4, space: space, bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.last.rawValue),
            provider: provider, decode: nil, shouldInterpolate: false, intent: .relativeColorimetric))
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).appendingPathExtension("png")
        defer { try? FileManager.default.removeItem(at: url) }
        let destination = try #require(CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil))
        CGImageDestinationAddImage(destination, image, nil)
        #expect(CGImageDestinationFinalize(destination))

        let result = try await PhotoColorSampler().sample(url, normalizedX: 0.5, normalizedY: 0.5).color
        let source = try #require(result.sourceRGB)
        #expect(abs(source.values.x - 64.0 / 255) < 0.003)
        #expect(abs(source.values.y - 128.0 / 255) < 0.003)
        #expect(abs(source.values.z - 192.0 / 255) < 0.003)
        #expect(abs(result.srgb.values.x - ColorConversions.encodeSRGB(64.0 / 255)) < 0.003)
        #expect(abs(source.values.x - result.srgb.values.x) > 0.2)
        #expect(result.sourceReadouts.contains { $0.label == "RGB" && $0.value == ColorConversions.text(source.values) })
    }

    @Test func rec2020IntegerAndCssReadoutsHaveOneEncoding() {
        let sample = ColorConversions.sample(linearP3: SIMD3(repeating: 0.18), x: 0, y: 0)
        #expect(abs(sample.rec2020.values.x - pow(0.18, 1 / 2.4)) < 0.00001)
        #expect(sample.rec2020.values == sample.cssRec2020.values)
        #expect(sample.readouts(.rec2020).contains { $0.label == "RGB (8-bit)" && $0.value == sample.cssRec2020.rgb })
    }
}
