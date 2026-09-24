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
}
