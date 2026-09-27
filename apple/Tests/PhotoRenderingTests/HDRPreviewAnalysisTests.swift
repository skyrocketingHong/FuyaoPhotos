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
        let preview = try await CardImageProcessor.shared.preview(source, card: PhotoCard(), hdr: true, maxDimension: 1024)
        #expect(preview.bitsPerComponent == 16)
        let maxValue = try maxComponent(preview)
        print("PREVIEW max linear value: \(maxValue)")
        #expect(maxValue > 1.0, "HDR preview must keep values above SDR white for EDR display")
    }

    @Test func previewToneMapsWhenHDRNotRequested() async throws {
        let source = try makeHDRHEIC()
        let preview = try await CardImageProcessor.shared.preview(source, card: PhotoCard(), hdr: false, maxDimension: 1024)
        // SDR render path: RGBA8 clamps at SDR white.
        #expect(preview.bitsPerComponent == 8)
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
        guard let data = (image.dataProvider?.data as Data?) else {
            throw MediaContainerError.invalid("no preview data")
        }
        let buffer = data.withUnsafeBytes { raw in Array(raw.bindMemory(to: UInt16.self)) }
        var maxValue: Float = 0
        for raw16 in buffer {
            let value = Self.float16(from: raw16)
            if value.isFinite && value > maxValue { maxValue = value }
        }
        return maxValue
    }

    static func float16(from raw: UInt16) -> Float {
        Float(Float16(bitPattern: raw))
    }
}
