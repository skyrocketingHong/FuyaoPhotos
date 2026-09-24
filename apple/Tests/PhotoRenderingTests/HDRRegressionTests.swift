import Testing
import Foundation
import CoreImage
import ImageIO
@testable import PhotoRenderingCore

struct HDRRegressionTests {
    @Test func sdrPreviewToneMapsNativePQInsteadOfClipping() async throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".heic")
        defer { try? FileManager.default.removeItem(at: url) }
        let context = CIContext()
        let image = CIImage(color: CIColor(red: 0.6, green: 0.7, blue: 0.8)).cropped(to: CGRect(x: 0, y: 0, width: 128, height: 128))
            .applyingFilter("CIExposureAdjust", parameters: [kCIInputEVKey: 3]).settingContentHeadroom(8)
        try context.writeHEIF10Representation(of: image, to: url,
            colorSpace: CGColorSpace(name: CGColorSpace.itur_2100_PQ)!, options: [:])
        let result = try await CardImageProcessor.shared.preview(url, card: PhotoCard(), hdr: false)
        let reference = try #require(CIImage(contentsOf: url, options: [.toneMapHDRtoSDR: true]))
        func pixel(_ image: CIImage) -> [UInt8] {
            var bytes = [UInt8](repeating: 0, count: 4)
            bytes.withUnsafeMutableBytes {
                context.render(image, toBitmap: $0.baseAddress!, rowBytes: 4,
                    bounds: CGRect(x: 0, y: 0, width: 1, height: 1), format: .RGBA8,
                    colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!)
            }
            return bytes
        }
        #expect(pixel(CIImage(cgImage: result)) == pixel(reference))
    }

    @Test func renderedPreviewCarriesDisplayHeadroom() async throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".heic")
        defer { try? FileManager.default.removeItem(at: url) }
        let context = CIContext()
        let sdr = CIImage(color: CIColor(red: 0.4, green: 0.5, blue: 0.6)).cropped(to: CGRect(x: 0, y: 0, width: 512, height: 384))
        let hdr = sdr.applyingFilter("CIExposureAdjust", parameters: [kCIInputEVKey: 3]).settingContentHeadroom(8)
        try context.writeHEIFRepresentation(of: sdr, to: url, format: .RGBA8,
            colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!, options: [.hdrImage: hdr])
        var card = PhotoCard(); card[.device] = "Camera"
        let image = try await CardImageProcessor.shared.preview(url, card: card, hdr: true)
        #expect(image.contentHeadroom > 1)
    }
}
