import Foundation
import CoreImage
import ImageIO
import UniformTypeIdentifiers
import AVFoundation

nonisolated struct CardDetailRender {
    let image: CGImage
    let cardRect: CGRect
    let textRects: [CardField: [CGRect]]
}

nonisolated struct CardOverlayRender {
    let image: CGImage
    let normalizedRect: CGRect
    let normalizedRadius: CGFloat
}

nonisolated struct CardPhotoMetadata: Sendable {
    var card: PhotoCard
    let width: Int
    let height: Int
    let latitude: Double?
    let longitude: Double?
    let hdr: Bool
    let hasPortraitData: Bool
    let kind: PhotoMediaKind
    let fileSize: Int
}

actor CardImageProcessor {
    static let shared = CardImageProcessor()
    private let context = CIContext(options: [.cacheIntermediates: false])

    func copyMovie(_ source: URL, to destination: URL, options: CardSaveOptions) async throws {
        try await LivePhotoMovie.copy(from: source, to: destination, options: options)
    }

    func read(_ url: URL, author: String, profiles: [LensProfile] = []) throws -> CardPhotoMetadata {
        let inspection = try PhotoMediaInspector.inspect(url)
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [String: Any],
              let image = CIImage(contentsOf: url, options: [.applyOrientationProperty: true]) else { throw CardError.invalidImage }
        let exif = properties[kCGImagePropertyExifDictionary as String] as? [String: Any] ?? [:]
        let tiff = properties[kCGImagePropertyTIFFDictionary as String] as? [String: Any] ?? [:]
        let gps = properties[kCGImagePropertyGPSDictionary as String] as? [String: Any] ?? [:]
        let maker = properties[kCGImagePropertyMakerAppleDictionary as String] as? [String: Any] ?? [:]
        var card = PhotoCard()
        card[.device] = tiff[kCGImagePropertyTIFFModel as String] as? String ?? ""
        card[.author] = tiff[kCGImagePropertyTIFFArtist as String] as? String ?? author
        let rawLens = exif[kCGImagePropertyExifLensModel as String] as? String ?? ""
        let namedCamera = AppleCameraNames.resolve(make: tiff[kCGImagePropertyTIFFMake as String] as? String ?? "",
            model: card[.device], lens: rawLens, cameraType: (maker["46"] as? NSNumber)?.intValue)
        card[.camera] = namedCamera?.name ?? Self.displayLensName(rawLens, device: card[.device])
        card[.imageSize] = Self.number(Double(inspection.width) * Double(inspection.height) / 1_000_000) + "MP"
        let equivalent = (exif[kCGImagePropertyExifFocalLenIn35mmFilm as String] as? NSNumber)?.doubleValue
        let physical = (exif[kCGImagePropertyExifFocalLength as String] as? NSNumber)?.doubleValue
        let lens = LensProfileResolver.resolve(model: card[.device], lens: rawLens, equivalent: equivalent,
                                                physical: physical, profiles: profiles)
        if let lens {
            card[.device] = lens.profile.device
            if card[.camera].isEmpty { card[.camera] = lens.profile.name }
        }
        if let focal = equivalent.flatMap({ $0.isFinite && $0 > 0 ? $0 : nil }) ?? lens?.equivalent {
            card[.focalLength] = Self.number(focal) + " MM"
            if let zoom = LensProfileResolver.explicitZoom(in: rawLens) ?? lens?.profile.zoom(for: focal)
                ?? namedCamera?.zoom(at: focal) {
                card[.focalLength] += " (\(Self.number(zoom, decimals: 1))X)"
            }
        }
        if let time = exif[kCGImagePropertyExifExposureTime as String] as? NSNumber, time.doubleValue > 0 {
            card[.exposure] = time.doubleValue < 1 ? "1/\(Int((1 / time.doubleValue).rounded()))" : Self.number(time.doubleValue) + "SEC"
        }
        if let aperture = exif[kCGImagePropertyExifFNumber as String] as? NSNumber { card[.aperture] = Self.number(aperture.doubleValue, decimals: 2) }
        if let iso = (exif[kCGImagePropertyExifISOSpeedRatings as String] as? [NSNumber])?.first { card[.iso] = iso.stringValue }
        card[.photographicStyle] = PhotographicStyleReader.name(properties: properties,
            metadata: CGImageSourceCopyMetadataAtIndex(source, 0, nil)) ?? ""
        let latitude = (gps[kCGImagePropertyGPSLatitude as String] as? NSNumber)?.doubleValue.mapSign(gps[kCGImagePropertyGPSLatitudeRef as String] as? String == "S")
        let longitude = (gps[kCGImagePropertyGPSLongitude as String] as? NSNumber)?.doubleValue.mapSign(gps[kCGImagePropertyGPSLongitudeRef as String] as? String == "W")
        let hdr = CGImageSourceCopyAuxiliaryDataInfoAtIndex(source, 0, kCGImageAuxiliaryDataTypeHDRGainMap) != nil ||
            CGImageSourceCopyAuxiliaryDataInfoAtIndex(source, 0, kCGImageAuxiliaryDataTypeISOGainMap) != nil ||
            (CIImage(contentsOf: url, options: [.expandToHDR: true])?.contentHeadroom ?? 1) > 1
        let portrait = [kCGImageAuxiliaryDataTypeDepth, kCGImageAuxiliaryDataTypeDisparity, kCGImageAuxiliaryDataTypePortraitEffectsMatte]
            .contains { CGImageSourceCopyAuxiliaryDataInfoAtIndex(source, 0, $0) != nil }
        return CardPhotoMetadata(card: card, width: Int(image.extent.width), height: Int(image.extent.height),
                                 latitude: latitude, longitude: longitude, hdr: hdr, hasPortraitData: portrait, kind: inspection.kind,
                                 fileSize: (try url.resourceValues(forKeys: [.fileSizeKey])).fileSize ?? 0)
    }

    func preview(_ url: URL, card: PhotoCard, hdr: Bool, maxDimension: CGFloat = 1800) throws -> CGImage {
        try Task.checkCancellation()
        if card.rows.isEmpty {
            guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
                  let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                    kCGImageSourceCreateThumbnailFromImageAlways: true,
                    kCGImageSourceCreateThumbnailWithTransform: true,
                    kCGImageSourceThumbnailMaxPixelSize: max(1, Int(maxDimension)),
                    kCGImageSourceDecodeRequest: hdr ? kCGImageSourceDecodeToHDR : kCGImageSourceDecodeToSDR,
                    kCGImageSourceGenerateImageSpecificLumaScaling: true
                  ] as CFDictionary) else { throw CardError.invalidImage }
            try Task.checkCancellation()
            return image
        }
        guard var image = CIImage(contentsOf: url, options: [.applyOrientationProperty: true, .expandToHDR: hdr, .toneMapHDRtoSDR: !hdr]) else { throw CardError.invalidImage }
        let scale = min(1, maxDimension / max(image.extent.width, image.extent.height))
        image = image.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        let rendered = try CardRenderer.render(image, card: card)
        try Task.checkCancellation()
        guard let result = context.createCGImage(rendered, from: rendered.extent, format: hdr ? .RGBAh : .RGBA8,
            colorSpace: CGColorSpace(name: hdr ? CGColorSpace.extendedLinearDisplayP3 : CGColorSpace.displayP3)!,
            deferred: false, calculateHDRStats: hdr) else { throw CardError.exportFailed }
        return result
    }

    func previewOverlay(_ url: URL, card: PhotoCard, maxDimension: CGFloat = 1800) throws -> CardOverlayRender? {
        guard !card.rows.isEmpty else { return nil }
        try Task.checkCancellation()
        guard var source = CIImage(contentsOf: url, options: [.applyOrientationProperty: true,
            .expandToHDR: false, .toneMapHDRtoSDR: true]) else { throw CardError.invalidImage }
        let scale = min(1, maxDimension / max(source.extent.width, source.extent.height))
        source = source.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        let layout = try CardLayout(size: source.extent.size, card: card)
        let rendered = try CardRenderer.render(source, card: card)
        guard let image = context.createCGImage(rendered, from: layout.rect, format: .RGBA8,
            colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!) else { throw CardError.exportFailed }
        return CardOverlayRender(image: image, normalizedRect: CGRect(
            x: layout.rect.minX / source.extent.width,
            y: (source.extent.height - layout.rect.maxY) / source.extent.height,
            width: layout.rect.width / source.extent.width, height: layout.rect.height / source.extent.height),
            normalizedRadius: layout.radius / source.extent.width)
    }

    func previewCardDetail(_ url: URL, card: PhotoCard, maxDimension: CGFloat = 1600,
                           aspectRatio: CGFloat? = nil) throws -> CardDetailRender {
        try Task.checkCancellation()
        guard !card.rows.isEmpty,
              var image = CIImage(contentsOf: url, options: [.applyOrientationProperty: true, .expandToHDR: false, .toneMapHDRtoSDR: true])
        else { throw CardError.invalidImage }
        let scale = min(1, maxDimension / max(image.extent.width, image.extent.height))
        image = image.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        let layout = try CardLayout(size: image.extent.size, card: card)
        let margin = max(6, min(layout.rect.width, layout.rect.height) * 0.08)
        var crop = layout.rect.insetBy(dx: -margin, dy: -margin).integral.intersection(image.extent)
        if let aspectRatio {
            guard aspectRatio.isFinite, (0.1...12).contains(aspectRatio) else { throw CardError.invalidImage }
            let subject = layout.rect.insetBy(dx: -margin, dy: -margin)
            let height = ceil(max(subject.height, subject.width / aspectRatio))
            let width = ceil(height * aspectRatio)
            crop = CGRect(x: floor(subject.midX - width / 2), y: floor(subject.midY - height / 2),
                          width: width, height: height)
        }
        guard crop.width > 0, crop.height > 0 else { throw CardError.invalidImage }
        let rendered = try CardRenderer.render(image, card: card)
            .composited(over: image.clampedToExtent())
            .cropped(to: crop)
        try Task.checkCancellation()
        let detailScale = min(1, maxDimension / max(crop.width, crop.height))
        let transform = CGAffineTransform(scaleX: detailScale, y: detailScale)
        let output = rendered.transformed(by: CGAffineTransform(translationX: -crop.minX, y: -crop.minY))
            .transformed(by: transform)
        let outputRect = CGRect(x: 0, y: 0, width: (crop.width * detailScale).rounded(),
                                height: (crop.height * detailScale).rounded())
        guard let detail = context.createCGImage(output, from: outputRect, format: .RGBA8,
            colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!, deferred: false)
        else { throw CardError.exportFailed }
        let cardRect = CGRect(x: layout.rect.minX - crop.minX,
                              y: crop.maxY - layout.rect.maxY,
                              width: layout.rect.width, height: layout.rect.height).applying(transform)
        let textRects = Dictionary(uniqueKeysWithValues: CardField.allCases.map { field in
            (field, layout.normalizedTextRects(for: field).map { rect in
                CGRect(x: cardRect.minX + rect.minX * cardRect.width,
                       y: cardRect.minY + rect.minY * cardRect.height,
                       width: rect.width * cardRect.width, height: rect.height * cardRect.height)
            })
        })
        return CardDetailRender(image: detail, cardRect: cardRect, textRects: textRects)
    }

    func export(_ url: URL, card: PhotoCard, options: CardSaveOptions, hdr: Bool, to destination: URL, live: Bool, liveIdentifier: String? = nil) throws {
        try Task.checkCancellation()
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let sdr = CIImage(contentsOf: url, options: [.applyOrientationProperty: true, .expandToHDR: false, .toneMapHDRtoSDR: true]) else { throw CardError.invalidImage }
        let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [String: Any] ?? [:]
        var metadata = Self.metadata(properties, options: options, live: live)
        if let liveIdentifier { metadata[kCGImagePropertyMakerAppleDictionary as String] = ["17": liveIdentifier] }
        let rendered = try CardRenderer.render(sdr, card: card).settingProperties(metadata)
        let qualityKey = CIImageRepresentationOption(rawValue: kCGImageDestinationLossyCompressionQuality as String)
        let orientation = CGImagePropertyOrientation(rawValue: (properties[kCGImagePropertyOrientation as String] as? NSNumber)?.uint32Value ?? 1) ?? .up
        let hasAuxiliaryData = PhotoAuxiliaryData.hasAuxiliaryData(source)
        var representation: [CIImageRepresentationOption: Any] = [:]
        guard options.format != .png || (!hasAuxiliaryData && !live && !hdr) else { throw CardError.hdrFormat }
        representation[qualityKey] = options.quality / 100
        if hdr {
            guard options.format != .png,
                  let hdrImage = CIImage(contentsOf: url, options: [.applyOrientationProperty: true, .expandToHDR: true]),
                  hdrImage.contentHeadroom > 1 else { throw CardError.hdrFormat }
            representation[.hdrImage] = try CardRenderer.render(hdrImage, card: card)
        }
        let colorSpace = CGColorSpace(name: CGColorSpace.displayP3)!
        do {
            if hasAuxiliaryData {
                try writeImageIO(base: rendered,
                    hdrImage: hdr ? representation[.hdrImage] as? CIImage : nil,
                    metadata: metadata, source: source, orientation: orientation, options: options, to: destination)
            } else {
                switch options.format {
                case .jpeg: try context.writeJPEGRepresentation(of: rendered, to: destination, colorSpace: colorSpace, options: representation)
                case .heic: try context.writeHEIFRepresentation(of: rendered, to: destination, format: .RGBA8, colorSpace: colorSpace, options: representation)
                case .png: try context.writePNGRepresentation(of: rendered, to: destination, format: .RGBA8, colorSpace: colorSpace)
                }
            }
            try verifyImage(destination, width: Int(sdr.extent.width), height: Int(sdr.extent.height), hdr: hdr)
            try PhotoAuxiliaryData.verify(source: source, output: CGImageSourceCreateWithURL(destination as CFURL, nil)!)
        } catch {
            try? FileManager.default.removeItem(at: destination)
            if (error as NSError).code == NSFileWriteOutOfSpaceError { throw CardError.storageFull }
            if hasAuxiliaryData { throw error }
            try writeImageIO(base: rendered,
                hdrImage: hdr ? representation[.hdrImage] as? CIImage : nil,
                metadata: metadata, source: source, orientation: orientation, options: options, to: destination)
        }
        guard let output = CGImageSourceCreateWithURL(destination as CFURL, nil),
              let written = CGImageSourceCopyPropertiesAtIndex(output, 0, nil) as? [String: Any],
              (written[kCGImagePropertyPixelWidth as String] as? NSNumber)?.intValue == Int(sdr.extent.width),
              (written[kCGImagePropertyPixelHeight as String] as? NSNumber)?.intValue == Int(sdr.extent.height) else { throw CardError.imageValidation }
        if hdr && CGImageSourceCopyAuxiliaryDataInfoAtIndex(output, 0, kCGImageAuxiliaryDataTypeHDRGainMap) == nil &&
            CGImageSourceCopyAuxiliaryDataInfoAtIndex(output, 0, kCGImageAuxiliaryDataTypeISOGainMap) == nil { throw CardError.imageValidation }
        try PhotoAuxiliaryData.verify(source: source, output: output)
        if live {
            let originalID = liveIdentifier ?? (properties[kCGImagePropertyMakerAppleDictionary as String] as? [String: Any])?["17"] as? String
            let outputID = (written[kCGImagePropertyMakerAppleDictionary as String] as? [String: Any])?["17"] as? String
            guard let originalID, originalID == outputID else { throw CardError.livePairing }
        }
    }

    private func verifyImage(_ url: URL, width: Int, height: Int, hdr: Bool) throws {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [String: Any],
              (properties[kCGImagePropertyPixelWidth as String] as? NSNumber)?.intValue == width,
              (properties[kCGImagePropertyPixelHeight as String] as? NSNumber)?.intValue == height else { throw CardError.imageValidation }
        if hdr && CGImageSourceCopyAuxiliaryDataInfoAtIndex(source, 0, kCGImageAuxiliaryDataTypeHDRGainMap) == nil &&
            CGImageSourceCopyAuxiliaryDataInfoAtIndex(source, 0, kCGImageAuxiliaryDataTypeISOGainMap) == nil { throw CardError.imageValidation }
    }

    // ImageIO handles auxiliary resources explicitly when the device's CI encoder omits them.
    // HDR must go through a pair encode: handing the encoder the expanded HDR image alone makes it
    // re-derive an SDR base darker than the photo's own base, and every SDR thumbnail of the
    // export then shows that darker base. Encoding base and HDR rendition as a pair keeps the
    // stored base; the derived gain map travels with it when the file is rebuilt around the pair.
    func writeImageIO(base: CIImage, hdrImage: CIImage?, metadata: [String: Any], source: CGImageSource,
        orientation: CGImagePropertyOrientation, options: CardSaveOptions, to url: URL) throws {
        if let hdrImage {
            let temporary = FileManager.default.temporaryDirectory
                .appendingPathComponent("FuyaoHDRPair-\(UUID().uuidString)")
                .appendingPathExtension(options.format.fileExtension)
            defer { try? FileManager.default.removeItem(at: temporary) }
            let qualityKey = CIImageRepresentationOption(rawValue: kCGImageDestinationLossyCompressionQuality as String)
            let colorSpace = CGColorSpace(name: CGColorSpace.displayP3)!
            if options.format == .jpeg {
                try context.writeJPEGRepresentation(of: base, to: temporary, colorSpace: colorSpace,
                    options: [qualityKey: options.quality / 100, .hdrImage: hdrImage])
            } else {
                try context.writeHEIFRepresentation(of: base, to: temporary, format: .RGBA8, colorSpace: colorSpace,
                    options: [qualityKey: options.quality / 100, .hdrImage: hdrImage])
            }
            let type: UTType = options.format == .jpeg ? .jpeg : .heic
            guard let pair = CGImageSourceCreateWithURL(temporary as CFURL, nil),
                  let destination = CGImageDestinationCreateWithURL(url as CFURL, type.identifier as CFString, 1, nil),
                  let gainMap = CGImageSourceCopyAuxiliaryDataInfoAtIndex(pair, 0, kCGImageAuxiliaryDataTypeISOGainMap)
            else { throw CardError.imageEncoding }
            var properties = metadata
            properties[kCGImageDestinationLossyCompressionQuality as String] = options.quality / 100
            CGImageDestinationAddImageFromSource(destination, pair, 0, properties as CFDictionary)
            CGImageDestinationAddAuxiliaryDataInfo(destination, kCGImageAuxiliaryDataTypeISOGainMap, gainMap)
            try PhotoAuxiliaryData.add(to: destination, source: source, orientation: orientation)
            guard CGImageDestinationFinalize(destination) else { throw CardError.imageEncoding }
            try verifyImage(url, width: Int(base.extent.width), height: Int(base.extent.height), hdr: true)
            return
        }
        let space = CGColorSpace(name: CGColorSpace.displayP3)!
        guard let rendered = context.createCGImage(base, from: base.extent, format: .RGBA8,
            colorSpace: space, deferred: false) else { throw CardError.imageEncoding }
        let type: UTType = options.format == .jpeg ? .jpeg : options.format == .heic ? .heic : .png
        guard let destination = CGImageDestinationCreateWithURL(url as CFURL, type.identifier as CFString, 1, nil) else { throw CardError.imageEncoding }
        var properties = metadata
        properties[kCGImageDestinationLossyCompressionQuality as String] = options.quality / 100
        CGImageDestinationAddImage(destination, rendered, properties as CFDictionary)
        try PhotoAuxiliaryData.add(to: destination, source: source, orientation: orientation)
        guard CGImageDestinationFinalize(destination) else { throw CardError.imageEncoding }
        try verifyImage(url, width: Int(base.extent.width), height: Int(base.extent.height), hdr: false)
    }

    nonisolated static func metadata(_ input: [String: Any], options: CardSaveOptions, live: Bool) -> [String: Any] {
        if options.keepExif && options.keepLocation && options.keepCaptureTime {
            var preserved = input
            preserved[kCGImagePropertyOrientation as String] = 1
            return preserved
        }
        var result: [String: Any] = [kCGImagePropertyOrientation as String: 1]
        let exif = input[kCGImagePropertyExifDictionary as String] as? [String: Any] ?? [:]
        let tiff = input[kCGImagePropertyTIFFDictionary as String] as? [String: Any] ?? [:]
        var outExif: [String: Any] = [:]
        var outTIFF: [String: Any] = [:]
        if options.keepExif {
            for key in [kCGImagePropertyExifExposureTime, kCGImagePropertyExifFNumber, kCGImagePropertyExifISOSpeedRatings,
                        kCGImagePropertyExifFocalLength, kCGImagePropertyExifFocalLenIn35mmFilm, kCGImagePropertyExifLensModel,
                        kCGImagePropertyExifLensMake, kCGImagePropertyExifExposureBiasValue, kCGImagePropertyExifWhiteBalance,
                        kCGImagePropertyExifFlash, kCGImagePropertyExifExposureProgram, kCGImagePropertyExifMeteringMode] {
                outExif[key as String] = exif[key as String]
            }
            for key in [kCGImagePropertyTIFFMake, kCGImagePropertyTIFFModel, kCGImagePropertyTIFFArtist, kCGImagePropertyTIFFCopyright] {
                outTIFF[key as String] = tiff[key as String]
            }
        }
        if options.keepCaptureTime {
            for key in ["DateTimeOriginal", "DateTimeDigitized", "SubsecTimeOriginal", "SubsecTimeDigitized", "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized"] {
                outExif[key] = exif[key]
            }
            outTIFF[kCGImagePropertyTIFFDateTime as String] = tiff[kCGImagePropertyTIFFDateTime as String]
        }
        if options.keepLocation {
            var gps = input[kCGImagePropertyGPSDictionary as String] as? [String: Any] ?? [:]
            if !options.keepCaptureTime { gps.removeValue(forKey: "DateStamp"); gps.removeValue(forKey: "TimeStamp") }
            result[kCGImagePropertyGPSDictionary as String] = gps
        }
        if live, let pair = (input[kCGImagePropertyMakerAppleDictionary as String] as? [String: Any])?["17"] {
            result[kCGImagePropertyMakerAppleDictionary as String] = ["17": pair]
        }
        result[kCGImagePropertyExifDictionary as String] = outExif
        result[kCGImagePropertyTIFFDictionary as String] = outTIFF
        return result
    }

    nonisolated static func number(_ value: Double, decimals: Int = 1) -> String {
        value.formatted(.number.locale(Locale(identifier: "en_US_POSIX")).precision(.fractionLength(0...decimals)).grouping(.never))
    }

    /// Apple devices write the EXIF LensModel with the device model as a leading prefix. The card
    /// already shows that model in the device line, so drop the prefix instead of repeating it.
    /// The prefix comes from the photo's own EXIF Model, never from a hardcoded device list.
    nonisolated static func displayLensName(_ lens: String, device: String) -> String {
        let trimmedDevice = device.split(whereSeparator: \.isWhitespace).joined(separator: " ")
        guard !trimmedDevice.isEmpty,
              lens.count >= trimmedDevice.count,
              lens.lowercased().hasPrefix(trimmedDevice.lowercased()) else { return lens }
        var remainder = lens.dropFirst(trimmedDevice.count)
        guard remainder.isEmpty || remainder.first?.isWhitespace == true else { return lens }
        remainder = remainder.drop(while: \.isWhitespace)
        // "iPhone 16 Pro" must not half-strip "iPhone 16 Pro Max …": Apple's lens descriptors
        // continue in lowercase ("back camera …") after the full model name.
        guard remainder.isEmpty || remainder.first?.isLowercase == true else { return lens }
        return String(remainder)
    }
}

private extension Double {
    nonisolated func mapSign(_ negative: Bool) -> Double { negative ? -self : self }
}
