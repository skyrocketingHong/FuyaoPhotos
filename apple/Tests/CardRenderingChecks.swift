import Foundation
import CoreImage
import CoreText
import ImageIO
import UniformTypeIdentifiers

@main struct CardRenderingChecks {
    static func main() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("fuyao-card-tests-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        func require(_ value: Bool, _ name: String) throws {
            guard value else { throw NSError(domain: "CardChecks", code: 1, userInfo: [NSLocalizedDescriptionKey: name]) }
        }
        var card = PhotoCard()
        card[.device] = "Xiaomi 17 Ultra by Leica"
        card[.author] = "摄影者"
        card[.camera] = "Leica 75-100mm Telephoto"
        card[.iso] = "100"
        let layout = try CardLayout(size: CGSize(width: 1527, height: 859), card: card)
        try require(layout.rect == CGRect(x: 1235, y: 35, width: 215, height: 168), "reference geometry")
        let large = try CardLayout(size: CGSize(width: 4080, height: 3072), card: card)
        try require(abs(large.rect.width - 215 * 3072 / 859) < 0.001, "short-edge scaling")
        var longer = card
        longer[.location] = String(repeating: "HONG KONG ", count: 30)
        try require(try CardLayout(size: CGSize(width: 1527, height: 859), card: longer).rect.height > layout.rect.height, "long text expands card")
        let one = CardTypography.text("1", size: 10.5, accent: false)
        let letter = CardTypography.text("H", size: 10.5, accent: false)
        let f1 = one.attribute(NSAttributedString.Key(kCTFontAttributeName as String), at: 0, effectiveRange: nil) as! CTFont
        let fh = letter.attribute(NSAttributedString.Key(kCTFontAttributeName as String), at: 0, effectiveRange: nil) as! CTFont
        try require(abs(CTFontGetSize(f1) / CTFontGetSize(fh) - 1.03) < 0.001, "shared digit 1 correction")
        print("Font:", CTFontCopyPostScriptName(fh))
        let size = CGRect(x: 0, y: 0, width: 1024, height: 768)
        let source = root.appendingPathComponent("source.jpg")
        let ci = CIImage(color: CIColor(red: 0.3, green: 0.4, blue: 0.5)).cropped(to: size)
        let context = CIContext()
        let cg = context.createCGImage(ci, from: size)!
        let metadata: [String: Any] = [
            kCGImagePropertyExifDictionary as String: ["FNumber": 2.39, "ISOSpeedRatings": [100], "LensModel": "Ultra Wide Camera", "DateTimeOriginal": "2026:09:21 12:30:00"],
            kCGImagePropertyTIFFDictionary as String: ["Model": "Test Camera", "Artist": "Example"],
            kCGImagePropertyGPSDictionary as String: ["Latitude": 22.3, "LatitudeRef": "N", "Longitude": 114.2, "LongitudeRef": "E", "DateStamp": "2026:09:21", "TimeStamp": "12:30:00"],
            kCGImagePropertyMakerAppleDictionary as String: ["17": "ca56b090-8659-46fe-bdbe-800db9f5c60a"]
        ]
        let destination = CGImageDestinationCreateWithURL(source as CFURL, UTType.jpeg.identifier as CFString, 1, nil)!
        CGImageDestinationAddImage(destination, cg, metadata as CFDictionary)
        try require(CGImageDestinationFinalize(destination), "fixture encoding")
        let processor = CardImageProcessor()
        let info = try await processor.read(source, author: "Default")
        try require(info.card[.author] == "Example" && info.card[.aperture] == "2.39", "EXIF import")
        try require(info.card[.camera] == "Ultra Wide Camera", "read lens name directly from EXIF")
        let orientationURL = root.appendingPathComponent("rotated.jpg")
        let rotated = CGImageDestinationCreateWithURL(orientationURL as CFURL, UTType.jpeg.identifier as CFString, 1, nil)!
        CGImageDestinationAddImage(rotated, cg, [kCGImagePropertyOrientation: 6] as CFDictionary)
        try require(CGImageDestinationFinalize(rotated), "orientation fixture")
        let rotatedInfo = try await processor.read(orientationURL, author: "")
        try require(rotatedInfo.width == 768 && rotatedInfo.height == 1024, "orientation normalized")
        try await processor.export(orientationURL, card: card, options: CardSaveOptions(), hdr: false,
                                   to: root.appendingPathComponent("rotated-output.jpg"), live: false)
        for format in CardExportFormat.allCases {
        for bits in 0..<8 {
            var options = CardSaveOptions()
            options.format = format
            options.keepExif = bits & 1 != 0
            options.keepLocation = bits & 2 != 0
            options.keepCaptureTime = bits & 4 != 0
            let output = root.appendingPathComponent("\(format.rawValue)-\(bits).\(format.fileExtension)")
            try await processor.export(source, card: card, options: options, hdr: false, to: output, live: false)
            let src = CGImageSourceCreateWithURL(output as CFURL, nil)!
            let props = CGImageSourceCopyPropertiesAtIndex(src, 0, nil) as! [String: Any]
            let exif = props[kCGImagePropertyExifDictionary as String] as? [String: Any] ?? [:]
            let gps = props[kCGImagePropertyGPSDictionary as String] as? [String: Any] ?? [:]
            try require((exif["FNumber"] != nil) == options.keepExif, "EXIF option \(bits)")
            try require((gps["Latitude"] != nil) == options.keepLocation, "GPS option \(bits)")
            try require((exif["DateTimeOriginal"] != nil) == options.keepCaptureTime, "date option \(bits)")
            if !options.keepCaptureTime { try require(gps["DateStamp"] == nil && gps["TimeStamp"] == nil, "GPS date stripped") }
            try checkMovie(root: root, options: options, require: require)
        }
        }
        var options = CardSaveOptions()
        options.format = .heic
        let hdrSource = root.appendingPathComponent("hdr.heic")
        let hdr = ci.applyingFilter("CIExposureAdjust", parameters: [kCIInputEVKey: 3]).settingContentHeadroom(8)
        try context.writeHEIFRepresentation(of: ci, to: hdrSource, format: .RGBA8,
            colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!, options: [.hdrImage: hdr])
        let hdrInfo = try await processor.read(hdrSource, author: "")
        try require(hdrInfo.hdr, "detect HDR")
        try await processor.export(hdrSource, card: card, options: options, hdr: true, to: root.appendingPathComponent("hdr-output.heic"), live: false)
        options.format = .jpeg
        let hdrJPEG = root.appendingPathComponent("hdr-output.jpg")
        try await processor.export(hdrSource, card: card, options: options, hdr: true, to: hdrJPEG, live: false)
        try require(try await processor.read(hdrJPEG, author: "").hdr, "read exported HDR JPEG")
        let decoded = CIImage(contentsOf: hdrJPEG, options: [.expandToHDR: true])!
        var pixel = [Float](repeating: 0, count: 4)
        pixel.withUnsafeMutableBytes { bytes in
            context.render(decoded, toBitmap: bytes.baseAddress!, rowBytes: 16, bounds: CGRect(x: 10, y: 10, width: 1, height: 1),
                format: .RGBAf, colorSpace: CGColorSpace(name: CGColorSpace.extendedLinearSRGB)!)
        }
        try require(pixel.prefix(3).max()! > 1, "actual HDR luminance survives outside card")
        try await processor.export(source, card: card, options: options, hdr: false, to: root.appendingPathComponent("live.jpg"), live: true)
        let fullPreview = try await processor.preview(source, card: card, hdr: false)
        let cardDetail = try await processor.previewCardDetail(source, card: card)
        try require(cardDetail.width > 0 && cardDetail.height > 0 &&
                    cardDetail.width < fullPreview.width && cardDetail.height < fullPreview.height,
                    "card detail renders only the card region")
        let rotatedDetail = try await processor.previewCardDetail(orientationURL, card: card)
        try require(rotatedDetail.width > 0 && rotatedDetail.height > 0, "rotated card detail")
        print("PASS: card geometry, wrapping, font scale, orientation, EXIF, 24 image / 8 movie metadata combinations, HDR JPEG/HEIC gain maps and luminance, Live Photo identifier, preview and card detail")
    }

    static func checkMovie(root: URL, options: CardSaveOptions, require: (Bool, String) throws -> Void) throws {
        func box(_ type: String, _ payload: Data) -> Data {
            var size = UInt32(payload.count + 8).bigEndian
            return withUnsafeBytes(of: &size) { Data($0) } + type.data(using: .isoLatin1)! + payload
        }
        let media = Data((0..<255).map(UInt8.init))
        let file = box("ftyp", Data("qt  ".utf8)) + box("moov",
            box("mvhd", Data(repeating: 0, count: 4) + Data(repeating: 7, count: 8) + Data(repeating: 0, count: 80)) +
            box("udta", box("©xyz", Data("+22.3+114.2/".utf8)) + box("©day", Data("2026-09-21".utf8)) + box("©mod", Data("Camera".utf8)))) + box("mdat", media)
        let original = root.appendingPathComponent("input.mov"), output = root.appendingPathComponent("output.mov")
        try file.write(to: original)
        try MovieMetadataCleaner.copy(from: original, to: output, options: options)
        let result = try Data(contentsOf: output)
        try require(result.count == file.count && result.suffix(media.count) == media, "movie media and offsets preserved")
        try require((result.range(of: Data("+22.3+114.2/".utf8)) != nil) == options.keepLocation, "movie GPS")
        try require((result.range(of: Data("2026-09-21".utf8)) != nil) == options.keepCaptureTime, "movie date")
        try require((result.range(of: Data("Camera".utf8)) != nil) == options.keepExif, "movie device")
    }
}
