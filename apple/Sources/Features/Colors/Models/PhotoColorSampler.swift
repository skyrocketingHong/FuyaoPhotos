import Foundation
import CoreImage
import ImageIO

nonisolated struct PhotoColorDescription: Sendable {
    let width: Int
    let height: Int
    let colorSpace: String
    let bits: Int
    let wideGamut: Bool
    let headroom: Float
    let gainMaps: [String]
}

nonisolated struct PhotoColorSample {
    let color: SampledPhotoColor
    let magnifier: CGImage?
}

actor PhotoColorSampler {
    static let shared = PhotoColorSampler()
    private let context = CIContext(options: [.cacheIntermediates: false])
    private var cachedURL: URL?
    private var cachedImage: CIImage?

    private func image(_ url: URL) throws -> CIImage {
        if cachedURL == url, let cachedImage { return cachedImage }
        guard let image = CIImage(contentsOf: url, options: [.applyOrientationProperty: true,
            .expandToHDR: false, .toneMapHDRtoSDR: false]) else { throw CocoaError(.fileReadCorruptFile) }
        cachedURL = url
        cachedImage = image
        return image
    }

    func describe(_ url: URL) throws -> PhotoColorDescription {
        let image = try image(url)
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [String: Any],
              let decoded = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceThumbnailMaxPixelSize: 128,
                kCGImageSourceDecodeRequest: kCGImageSourceDecodeToHDR
              ] as CFDictionary) else { throw CocoaError(.fileReadCorruptFile) }
        let types = [(kCGImageAuxiliaryDataTypeHDRGainMap, "Apple"), (kCGImageAuxiliaryDataTypeISOGainMap, "ISO 21496-1")]
        return PhotoColorDescription(width: Int(image.extent.width), height: Int(image.extent.height),
            colorSpace: properties[kCGImagePropertyProfileName as String] as? String ?? image.colorSpace?.name as String? ?? "RGB",
            bits: (properties[kCGImagePropertyDepth as String] as? NSNumber)?.intValue ?? decoded.bitsPerComponent,
            wideGamut: image.colorSpace?.isWideGamutRGB == true, headroom: decoded.contentHeadroom,
            gainMaps: types.compactMap { CGImageSourceCopyAuxiliaryDataInfoAtIndex(source, 0, $0.0) == nil ? nil : $0.1 })
    }

    func sample(_ url: URL, normalizedX: Double, normalizedY: Double) throws -> PhotoColorSample {
        try Task.checkCancellation()
        let image = try image(url)
        let x = min(Int(image.extent.width) - 1, max(0, Int(normalizedX * image.extent.width)))
        let y = min(Int(image.extent.height) - 1, max(0, Int(normalizedY * image.extent.height)))
        let rect = CGRect(x: image.extent.minX + CGFloat(x), y: image.extent.maxY - CGFloat(y) - 1, width: 1, height: 1)
        var pixel = [Float](repeating: 0, count: 4)
        pixel.withUnsafeMutableBytes {
            context.render(image, toBitmap: $0.baseAddress!, rowBytes: 16, bounds: rect, format: .RGBAf,
                colorSpace: CGColorSpace(name: CGColorSpace.extendedLinearDisplayP3)!)
        }
        guard pixel.allSatisfy(\.isFinite) else { throw CocoaError(.fileReadCorruptFile) }
        let alpha = pixel[3] > 0 ? pixel[3] : 1
        let color = ColorConversions.sample(linearP3: SIMD3(Double(pixel[0] / alpha), Double(pixel[1] / alpha), Double(pixel[2] / alpha)), x: x, y: y)
        let crop = rect.insetBy(dx: -12, dy: -12).intersection(image.extent)
        let patch = context.createCGImage(image, from: crop, format: .RGBA8, colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!)
        try Task.checkCancellation()
        return PhotoColorSample(color: color, magnifier: patch)
    }
}
