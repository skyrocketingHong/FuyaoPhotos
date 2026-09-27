import Testing
import Foundation
import CoreImage
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers
@testable import PhotoRenderingCore

/// The card preview supplies extended-linear pixels to the system image view for
/// display mapping; switching HDR off produces a tone-mapped SDR image.
struct HDRPreviewAnalysisTests {
    @Test func previewCarriesHeadroomWhenHDRRequested() async throws {
        let source = try makeHDRHEIC()
        defer { try? FileManager.default.removeItem(at: source.deletingLastPathComponent()) }
        let preview = try await CardImageProcessor.shared.preview(source, card: PhotoCard(), hdr: true, maxDimension: 1024)
        #expect(preview.bitsPerComponent >= 10)
        let maxValue = try maxComponent(preview)
        #expect(preview.contentHeadroom > 1)
        #expect(preview.shouldToneMap)
        #expect(maxValue > 1.0, "HDR preview must keep values above SDR white for EDR display")
    }

    @Test func previewToneMapsWhenHDRNotRequested() async throws {
        let source = try makeHDRHEIC()
        defer { try? FileManager.default.removeItem(at: source.deletingLastPathComponent()) }
        let preview = try await CardImageProcessor.shared.preview(source, card: PhotoCard(), hdr: false, maxDimension: 1024)
        #expect(preview.bitsPerComponent == 8)
    }

    @Test(.enabled(if: ProcessInfo.processInfo.environment["FUYAO_HDR_SAMPLE"] != nil))
    func nativePreviewRetainsOriginalAdaptiveMapping() async throws {
        let path = try #require(ProcessInfo.processInfo.environment["FUYAO_HDR_SAMPLE"])
        let url = URL(fileURLWithPath: path)
        let source = try #require(CGImageSourceCreateWithURL(url as CFURL, nil))
        let original = try #require(CGImageSourceCreateImageAtIndex(source, 0,
            [kCGImageSourceDecodeRequest: kCGImageSourceDecodeToHDR] as CFDictionary))
        let preview = try await CardImageProcessor.shared.preview(url, card: PhotoCard(), hdr: true)
        #expect(abs(preview.contentHeadroom - original.contentHeadroom) < 0.001)
        #expect(preview.containsImageSpecificToneMappingMetadata)
        let sdr = try await CardImageProcessor.shared.preview(url, card: PhotoCard(), hdr: false)
        #expect(sdr.contentHeadroom <= 1)
        #expect(try maxComponent(preview) > maxComponent(sdr))
    }

    // MARK: Helpers

    private func makeHDRHEIC() throws -> URL {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let url = folder.appendingPathComponent("hdr.heic")
        let bounds = CGRect(x: 0, y: 0, width: 512, height: 384)
        let base = CIImage(color: CIColor(red: 0.4, green: 0.3, blue: 0.5)).cropped(to: bounds)
        let hdrImage = base.applyingFilter("CIExposureAdjust", parameters: [kCIInputEVKey: 3]).settingContentHeadroom(4)
        try CIContext().writeHEIFRepresentation(of: base, to: url, format: .RGBA8,
                                                colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!,
                                                options: [.hdrImage: hdrImage])
        return url
    }

    private func maxComponent(_ image: CGImage) throws -> Float {
        let input = CIImage(cgImage: image)
        let scaled = input.transformed(by: CGAffineTransform(scaleX: 64 / input.extent.width, y: 64 / input.extent.height))
        var values = [Float](repeating: 0, count: 64 * 64 * 4)
        values.withUnsafeMutableBytes {
            CIContext().render(scaled, toBitmap: $0.baseAddress!, rowBytes: 64 * 16,
                bounds: CGRect(x: 0, y: 0, width: 64, height: 64), format: .RGBAf,
                colorSpace: CGColorSpace(name: CGColorSpace.extendedLinearDisplayP3)!)
        }
        return values.enumerated().filter { $0.offset % 4 != 3 }.map(\.element).max() ?? 0
    }

    static func float16(from raw: UInt16) -> Float {
        Float(Float16(bitPattern: raw))
    }
}
