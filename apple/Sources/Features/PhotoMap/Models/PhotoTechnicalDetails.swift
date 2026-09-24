import Foundation
import ImageIO
import UniformTypeIdentifiers

nonisolated struct PhotoTechnicalDetails: Sendable {
    let typeDescription: String?
    let colorProfile: String?
    let horizontalDPI: Double?
    let verticalDPI: Double?
    let capturedAt: String?
    let deviceMake: String?
    let deviceModel: String?
    let lensModel: String?
    let apertureValue: Double?
    let exposureTime: Double?
    let exposureProgram: Int?
    let focalLength: Double?
    let iso: Int?
    let flash: Int?
    let fNumber: Double?
    let meteringMode: Int?
    let whiteBalance: Int?
    let artist: String?
}

actor PhotoTechnicalDetailsReader {
    static let shared = PhotoTechnicalDetailsReader()

    func read(_ url: URL) -> PhotoTechnicalDetails? {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [String: Any]
        else { return nil }
        let exif = properties[kCGImagePropertyExifDictionary as String] as? [String: Any] ?? [:]
        let tiff = properties[kCGImagePropertyTIFFDictionary as String] as? [String: Any] ?? [:]
        let typeDescription = (CGImageSourceGetType(source) as String?).flatMap { UTType($0)?.localizedDescription }
        return PhotoTechnicalDetails(
            typeDescription: typeDescription,
            colorProfile: properties[kCGImagePropertyProfileName as String] as? String,
            horizontalDPI: (properties[kCGImagePropertyDPIWidth as String] as? NSNumber)?.doubleValue,
            verticalDPI: (properties[kCGImagePropertyDPIHeight as String] as? NSNumber)?.doubleValue,
            capturedAt: exif[kCGImagePropertyExifDateTimeOriginal as String] as? String,
            deviceMake: tiff[kCGImagePropertyTIFFMake as String] as? String,
            deviceModel: tiff[kCGImagePropertyTIFFModel as String] as? String,
            lensModel: exif[kCGImagePropertyExifLensModel as String] as? String,
            apertureValue: (exif[kCGImagePropertyExifApertureValue as String] as? NSNumber)?.doubleValue,
            exposureTime: (exif[kCGImagePropertyExifExposureTime as String] as? NSNumber)?.doubleValue,
            exposureProgram: (exif[kCGImagePropertyExifExposureProgram as String] as? NSNumber)?.intValue,
            focalLength: (exif[kCGImagePropertyExifFocalLength as String] as? NSNumber)?.doubleValue,
            iso: (exif[kCGImagePropertyExifISOSpeedRatings as String] as? [NSNumber])?.first?.intValue,
            flash: (exif[kCGImagePropertyExifFlash as String] as? NSNumber)?.intValue,
            fNumber: (exif[kCGImagePropertyExifFNumber as String] as? NSNumber)?.doubleValue,
            meteringMode: (exif[kCGImagePropertyExifMeteringMode as String] as? NSNumber)?.intValue,
            whiteBalance: (exif[kCGImagePropertyExifWhiteBalance as String] as? NSNumber)?.intValue,
            artist: tiff[kCGImagePropertyTIFFArtist as String] as? String
        )
    }
}
