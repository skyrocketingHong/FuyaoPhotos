import Foundation
import ImageIO
import CoreImage
import AVFoundation

nonisolated enum PhotoAuxiliaryData {
    static var types: [CFString] {
        [kCGImageAuxiliaryDataTypeDepth, kCGImageAuxiliaryDataTypeDisparity,
         kCGImageAuxiliaryDataTypePortraitEffectsMatte, kCGImageAuxiliaryDataTypeSemanticSegmentationSkinMatte,
         kCGImageAuxiliaryDataTypeSemanticSegmentationHairMatte, kCGImageAuxiliaryDataTypeSemanticSegmentationTeethMatte,
         kCGImageAuxiliaryDataTypeSemanticSegmentationGlassesMatte, kCGImageAuxiliaryDataTypeSemanticSegmentationSkyMatte]
    }

    static func representation(source: CGImageSource, orientation: CGImagePropertyOrientation) throws -> [CIImageRepresentationOption: Any] {
        var result: [CIImageRepresentationOption: Any] = [:]
        var segmentation: [AVSemanticSegmentationMatte] = []
        for type in types {
            guard let info = CGImageSourceCopyAuxiliaryDataInfoAtIndex(source, 0, type) as? [AnyHashable: Any] else { continue }
            if type == kCGImageAuxiliaryDataTypeDepth || type == kCGImageAuxiliaryDataTypeDisparity {
                result[.avDepthData] = try AVDepthData(fromDictionaryRepresentation: info).applyingExifOrientation(orientation)
            } else if type == kCGImageAuxiliaryDataTypePortraitEffectsMatte {
                result[.avPortraitEffectsMatte] = try AVPortraitEffectsMatte(fromDictionaryRepresentation: info).applyingExifOrientation(orientation)
            } else {
                segmentation.append(try AVSemanticSegmentationMatte(fromImageSourceAuxiliaryDataType: type, dictionaryRepresentation: info).applyingExifOrientation(orientation))
            }
        }
        if !segmentation.isEmpty { result[.avSemanticSegmentationMattes] = segmentation }
        return result
    }

    static func verify(source: CGImageSource, output: CGImageSource) throws {
        for type in types where CGImageSourceCopyAuxiliaryDataInfoAtIndex(source, 0, type) != nil {
            if type == kCGImageAuxiliaryDataTypeDepth || type == kCGImageAuxiliaryDataTypeDisparity {
                guard [kCGImageAuxiliaryDataTypeDepth,kCGImageAuxiliaryDataTypeDisparity].contains(where: {
                    CGImageSourceCopyAuxiliaryDataInfoAtIndex(output,0,$0) != nil
                }) else { throw CardError.auxiliaryEncoding }
            } else if CGImageSourceCopyAuxiliaryDataInfoAtIndex(output,0,type) == nil { throw CardError.auxiliaryEncoding }
        }
    }

    static func add(to destination: CGImageDestination, source: CGImageSource, orientation: CGImagePropertyOrientation) throws {
        for type in types {
            guard let info = CGImageSourceCopyAuxiliaryDataInfoAtIndex(source,0,type) as? [AnyHashable:Any] else { continue }
            if orientation == .up {
                CGImageDestinationAddAuxiliaryDataInfo(destination, type, info as CFDictionary)
                continue
            }
            if type != kCGImageAuxiliaryDataTypeDepth && type != kCGImageAuxiliaryDataTypeDisparity,
               let oriented = orientedMonochrome(info, orientation: orientation) {
                CGImageDestinationAddAuxiliaryDataInfo(destination, type, oriented as CFDictionary)
                continue
            }
            var writtenType: NSString?
            let dictionary: [AnyHashable:Any]?
            if type == kCGImageAuxiliaryDataTypeDepth || type == kCGImageAuxiliaryDataTypeDisparity {
                dictionary = try AVDepthData(fromDictionaryRepresentation: info).applyingExifOrientation(orientation)
                    .dictionaryRepresentation(forAuxiliaryDataType: &writtenType)
            } else if type == kCGImageAuxiliaryDataTypePortraitEffectsMatte {
                dictionary = try AVPortraitEffectsMatte(fromDictionaryRepresentation: info).applyingExifOrientation(orientation)
                    .dictionaryRepresentation(forAuxiliaryDataType: &writtenType)
            } else {
                dictionary = try AVSemanticSegmentationMatte(fromImageSourceAuxiliaryDataType: type, dictionaryRepresentation: info)
                    .applyingExifOrientation(orientation).dictionaryRepresentation(forAuxiliaryDataType: &writtenType)
            }
            guard let writtenType,let dictionary else { throw CardError.auxiliaryEncoding }
            CGImageDestinationAddAuxiliaryDataInfo(destination,writtenType as CFString,dictionary as CFDictionary)
        }
    }

    static func orientedMonochrome(_ info: [AnyHashable: Any], orientation: CGImagePropertyOrientation) -> [AnyHashable: Any]? {
        guard var description = info[kCGImageAuxiliaryDataInfoDataDescription] as? [String: Any],
              let width = description["Width"] as? Int, let height = description["Height"] as? Int,
              let stride = description["BytesPerRow"] as? Int,
              let format = description["PixelFormat"] as? UInt32, format == kCVPixelFormatType_OneComponent8,
              width > 0, height > 0, stride >= width, width <= 20_000, height <= 20_000,
              let bytes = info[kCGImageAuxiliaryDataInfoData] as? Data,
              stride <= bytes.count / height, width * height <= 64_000_000 else { return nil }
        let swap = orientation.rawValue >= 5
        let outputWidth = swap ? height : width
        let outputHeight = swap ? width : height
        var output = Data(count: outputWidth * outputHeight)
        for y in 0..<height {
            for x in 0..<width {
                let point: (Int, Int)
                switch orientation {
                case .up: point = (x, y)
                case .upMirrored: point = (width - 1 - x, y)
                case .down: point = (width - 1 - x, height - 1 - y)
                case .downMirrored: point = (x, height - 1 - y)
                case .leftMirrored: point = (y, x)
                case .right: point = (height - 1 - y, x)
                case .rightMirrored: point = (height - 1 - y, width - 1 - x)
                case .left: point = (y, width - 1 - x)
                @unknown default: return nil
                }
                output[point.1 * outputWidth + point.0] = bytes[y * stride + x]
            }
        }
        description["Width"] = outputWidth
        description["Height"] = outputHeight
        description["BytesPerRow"] = outputWidth
        var result = info
        result[kCGImageAuxiliaryDataInfoData] = output
        result[kCGImageAuxiliaryDataInfoDataDescription] = description
        return result
    }
}
