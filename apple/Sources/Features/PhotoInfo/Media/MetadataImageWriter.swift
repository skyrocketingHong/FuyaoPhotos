import Foundation
import ImageIO

nonisolated enum MetadataImageWriter {
    static func write(_ sourceURL: URL, to outputURL: URL, options: CardSaveOptions) throws {
        guard let source = CGImageSourceCreateWithURL(sourceURL as CFURL, nil),
              let type = CGImageSourceGetType(source),
              let original = CGImageSourceCopyMetadataAtIndex(source, 0, nil),
              let changes = CGImageMetadataCreateMutableCopy(original),
              let destination = CGImageDestinationCreateWithURL(outputURL as CFURL, type, 1, nil)
        else { throw CardError.invalidImage }
        let capture: Set<String> = ["ExposureTime", "FNumber", "ISOSpeedRatings", "PhotographicSensitivity",
            "FocalLength", "FocalLengthIn35mmFilm", "LensModel", "LensMake", "LensInfo", "LensSpecification",
            "ExposureBiasValue", "WhiteBalance", "Flash", "ExposureProgram", "MeteringMode", "Make", "Model",
            "Artist", "Copyright", "BodySerialNumber", "LensSerialNumber", "CameraOwnerName"]
        let dates: Set<String> = ["DateTime", "DateTimeOriginal", "DateTimeDigitized", "SubsecTime", "SubsecTimeOriginal",
            "SubsecTimeDigitized", "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized", "CreateDate", "ModifyDate",
            "DateCreated", "GPSDateStamp", "GPSTimeStamp"]
        var paths: [String] = []
        CGImageMetadataEnumerateTagsUsingBlock(original, nil, [kCGImageMetadataEnumerateRecursively: true] as CFDictionary) { path, tag in
            let name = CGImageMetadataTagCopyName(tag) as String? ?? ""
            if (!options.keepExif && capture.contains(name)) || (!options.keepCaptureTime && dates.contains(name)) {
                paths.append(path as String)
            }
            return true
        }
        for path in paths {
            guard CGImageMetadataRemoveTagWithPath(changes, nil, path as CFString) else { throw CardError.imageValidation }
        }
        var error: Unmanaged<CFError>?
        guard CGImageDestinationCopyImageSource(destination, source, [
            kCGImageDestinationMetadata: changes,
            kCGImageDestinationMergeMetadata: false,
            kCGImageMetadataShouldExcludeGPS: !options.keepLocation,
            kCGImageDestinationPreserveGainMap: true
        ] as CFDictionary, &error) else {
            try? FileManager.default.removeItem(at: outputURL)
            throw error?.takeRetainedValue() ?? CardError.imageEncoding as Error
        }
        guard let output = CGImageSourceCreateWithURL(outputURL as CFURL, nil) else { throw CardError.imageValidation }
        try PhotoAuxiliaryData.verify(source: source, output: output)
        for gain in [kCGImageAuxiliaryDataTypeHDRGainMap, kCGImageAuxiliaryDataTypeISOGainMap]
        where CGImageSourceCopyAuxiliaryDataInfoAtIndex(source, 0, gain) != nil {
            guard CGImageSourceCopyAuxiliaryDataInfoAtIndex(output, 0, gain) != nil else { throw CardError.imageValidation }
        }
        let before = StyleInjection.stylesCoverage(in: sourceURL)
        let after = StyleInjection.stylesCoverage(in: outputURL)
        guard (!before.photographic || after.photographic), (!before.texture || after.texture) else { throw CardError.imageValidation }
    }
}
