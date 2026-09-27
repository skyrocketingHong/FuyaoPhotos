import AVFoundation
import Foundation
import ImageIO
import CoreVideo

nonisolated struct PortraitDepthLayerPixels: Sendable {
    let bytes: Data
    let width: Int
    let height: Int
    let isDisparity: Bool
}

/// Reads only an embedded depth or disparity plane from the imported original.
nonisolated enum PortraitDepthLayerReader {
    static func readAsync(_ url: URL) async -> PortraitDepthLayerPixels? {
        let loading = Task.detached(priority: .utility) { read(url) }
        return await withTaskCancellationHandler {
            await loading.value
        } onCancel: {
            loading.cancel()
        }
    }

    static func read(_ url: URL) -> PortraitDepthLayerPixels? {
        guard !Task.isCancelled,
              let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [String: Any]
        let orientationValue = (properties?[kCGImagePropertyOrientation as String] as? NSNumber)?.uint32Value ?? 1
        let orientation = CGImagePropertyOrientation(rawValue: orientationValue) ?? .up

        for (type, isDisparity) in [(kCGImageAuxiliaryDataTypeDepth, false),
                                    (kCGImageAuxiliaryDataTypeDisparity, true)] {
            guard !Task.isCancelled else { return nil }
            guard let info = CGImageSourceCopyAuxiliaryDataInfoAtIndex(source, 0, type) as? [AnyHashable: Any],
                  let depth = try? AVDepthData(fromDictionaryRepresentation: info)
                    .applyingExifOrientation(orientation),
                  let pixels = render(depth, isDisparity: isDisparity) else { continue }
            return pixels
        }
        return nil
    }

    private static func render(_ depth: AVDepthData, isDisparity: Bool) -> PortraitDepthLayerPixels? {
        let format = isDisparity ? kCVPixelFormatType_DisparityFloat32 : kCVPixelFormatType_DepthFloat32
        guard depth.depthDataType == format || depth.availableDepthDataTypes.contains(format) else { return nil }
        let sourceBuffer = depth.depthDataMap
        let sourceWidth = CVPixelBufferGetWidth(sourceBuffer)
        let sourceHeight = CVPixelBufferGetHeight(sourceBuffer)
        guard sourceWidth > 0, sourceHeight > 0,
              Double(sourceWidth) * Double(sourceHeight) <= 64_000_000 else { return nil }
        let converted = depth.converting(toDepthDataType: format)
        let buffer = converted.depthDataMap
        let width = CVPixelBufferGetWidth(buffer)
        let height = CVPixelBufferGetHeight(buffer)
        guard width > 0, height > 0, width <= 20_000, height <= 20_000,
              Double(width) * Double(height) <= 64_000_000,
              CVPixelBufferGetPixelFormatType(buffer) == format,
              CVPixelBufferLockBaseAddress(buffer, .readOnly) == kCVReturnSuccess else { return nil }
        defer { CVPixelBufferUnlockBaseAddress(buffer, .readOnly) }
        guard let base = CVPixelBufferGetBaseAddress(buffer),
              CVPixelBufferGetBytesPerRow(buffer) >= width * MemoryLayout<Float>.size else { return nil }
        let stride = CVPixelBufferGetBytesPerRow(buffer) / MemoryLayout<Float>.size
        let values = base.assumingMemoryBound(to: Float.self)

        let sampleStep = max(1, Int((Double(width) * Double(height) / 20_000).squareRoot()))
        var samples: [Float] = []
        samples.reserveCapacity(25_000)
        for y in Swift.stride(from: 0, to: height, by: sampleStep) {
            guard !Task.isCancelled else { return nil }
            for x in Swift.stride(from: 0, to: width, by: sampleStep) {
                let value = values[y * stride + x]
                if value.isFinite && value > 0 { samples.append(value) }
            }
        }
        guard samples.count > 1 else { return nil }
        samples.sort()
        let lower = samples[Int(Double(samples.count - 1) * 0.01)]
        let upper = samples[Int(Double(samples.count - 1) * 0.99)]
        guard upper - lower > Float.ulpOfOne else { return nil }

        let scale = min(1, 1_400.0 / Double(max(width, height)))
        let outputWidth = max(1, Int(Double(width) * scale))
        let outputHeight = max(1, Int(Double(height) * scale))
        var bytes = Data(count: outputWidth * outputHeight)
        bytes.withUnsafeMutableBytes { output in
            guard let output = output.baseAddress?.assumingMemoryBound(to: UInt8.self) else { return }
            for y in 0..<outputHeight {
                let sourceY = min(height - 1, y * height / outputHeight)
                for x in 0..<outputWidth {
                    let sourceX = min(width - 1, x * width / outputWidth)
                    let value = values[sourceY * stride + sourceX]
                    guard value.isFinite && value > 0 else { output[y * outputWidth + x] = 0; continue }
                    let fraction = max(0, min(1, (value - lower) / (upper - lower)))
                    output[y * outputWidth + x] = UInt8((isDisparity ? fraction : 1 - fraction) * 255)
                }
            }
        }
        guard !Task.isCancelled else { return nil }
        return PortraitDepthLayerPixels(bytes: bytes, width: outputWidth, height: outputHeight,
                                        isDisparity: isDisparity)
    }
}
