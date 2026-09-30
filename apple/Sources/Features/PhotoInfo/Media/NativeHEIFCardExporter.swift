import Foundation
import CoreImage
import ImageIO
import UniformTypeIdentifiers

nonisolated enum NativeHEIFCardExporter {
    static func hasEditingPayloads(_ container: HeifContainer) -> Bool {
        let coverage = container.stylesCoverage
        return coverage.photographic || coverage.texture || container.items.contains { item in
            guard let urn = container.auxCURN(of: item.id) else { return false }
            return !urn.contains("hdrgainmap") && !urn.contains("21496")
        }
    }

    static func requiresHEIC(_ container: HeifContainer) -> Bool {
        container.stylesCoverage.photographic || container.stylesCoverage.texture
            || container.items.contains { container.auxCURN(of: $0.id)?.contains("tag:apple.com,2026:") == true }
    }

    static func write(sourceURL: URL, source: CGImageSource, upright: CIImage, card: PhotoCard,
                      options: CardSaveOptions, context: CIContext, destination: URL) throws {
        let work = FileManager.default.temporaryDirectory.appendingPathComponent("FuyaoNativeCard-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: work, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: work) }
        let base: URL
        if options.keepExif && options.keepLocation && options.keepCaptureTime { base = sourceURL }
        else {
            base = work.appendingPathComponent("metadata.heic")
            try MetadataImageWriter.write(sourceURL, to: base, options: options)
        }
        var container = try HeifContainer.load(fileURL: base)
        let original = container
        guard container.embeddedMotionLength == nil, !container.compatibleBrands.contains("avif"),
              let size = container.dimensions(of: container.primary),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [String: Any] else {
            throw CardError.unsupportedMedia
        }
        let orientation = CGImagePropertyOrientation(rawValue: (properties[kCGImagePropertyOrientation as String] as? NSNumber)?.uint32Value ?? 1) ?? .up
        let inverse: CGImagePropertyOrientation = orientation == .right ? .left : orientation == .left ? .right : orientation
        let stored = upright.oriented(inverse)
        guard Int(stored.extent.width) == size.width, Int(stored.extent.height) == size.height else { throw CardError.imageValidation }

        let stage = work.appendingPathComponent("rendered.heic")
        let isoGain = CGImageSourceCopyAuxiliaryDataInfoAtIndex(source, 0, kCGImageAuxiliaryDataTypeHDRGainMap) == nil
            && CGImageSourceCopyAuxiliaryDataInfoAtIndex(source, 0, kCGImageAuxiliaryDataTypeISOGainMap) != nil
        var gainURN: String?
        if isoGain {
            guard let hdr = CIImage(contentsOf: sourceURL, options: [.applyOrientationProperty: true, .expandToHDR: true]) else {
                throw CardError.auxiliaryEncoding
            }
            let renderedHDR = try CardRenderer.render(hdr, card: card).oriented(inverse)
            try context.writeHEIFRepresentation(of: stored, to: stage, format: .RGBA8,
                colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!, options: [
                    .hdrImage: renderedHDR,
                    CIImageRepresentationOption(rawValue: kCGImageDestinationLossyCompressionQuality as String): options.quality / 100
                ])
            gainURN = "urn:iso:std:iso:ts:21496:-1"
        } else {
            guard let writer = CGImageDestinationCreateWithURL(stage as CFURL, UTType.heic.identifier as CFString, 1, nil),
              let image = context.createCGImage(stored, from: stored.extent, format: .RGBA8,
                colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!, deferred: false) else { throw CardError.imageEncoding }
            var stageProperties = properties
            stageProperties[kCGImagePropertyOrientation as String] = 1
            stageProperties[kCGImageDestinationLossyCompressionQuality as String] = options.quality / 100
            CGImageDestinationAddImage(writer, image, stageProperties as CFDictionary)
            if let info = CGImageSourceCopyAuxiliaryDataInfoAtIndex(source, 0, kCGImageAuxiliaryDataTypeHDRGainMap) as? [String: Any] {
                let adjusted = try gainMapWithCard(info, card: card, displaySize: upright.extent.size,
                    storedSize: CGSize(width: size.width, height: size.height), orientation: orientation)
                CGImageDestinationAddAuxiliaryDataInfo(writer, kCGImageAuxiliaryDataTypeHDRGainMap, adjusted as CFDictionary)
                gainURN = "urn:com:apple:photo:2020:aux:hdrgainmap"
            }
            guard CGImageDestinationFinalize(writer) else { throw CardError.imageEncoding }
        }
        let replacement = try HeifContainer.load(fileURL: stage)
        var changedRoots = Set<UInt32>([container.primary])
        try container.replaceImage(container.primary, from: replacement, root: replacement.primary)
        if let gainURN {
            func gainID(_ graph: HeifContainer) throws -> UInt32 {
                if let item = graph.items.single(where: { graph.auxCURN(of: $0.id) == gainURN }) { return item.id }
                guard isoGain, graph.tmapIDs.count == 1,
                      let reference = graph.references.single(where: { $0.type == "dimg" && $0.from == graph.tmapIDs[0] }),
                      reference.to.count == 2, reference.to[0] == graph.primary else { throw CardError.auxiliaryEncoding }
                return reference.to[1]
            }
            let oldGain = try gainID(original), newGain = try gainID(replacement)
            try container.replaceImage(oldGain, from: replacement, root: newGain)
            changedRoots.insert(oldGain)
            if isoGain {
                guard original.tmapIDs.count == 1, replacement.tmapIDs.count == 1 else { throw CardError.auxiliaryEncoding }
                try container.rehomeItemPayload(id: original.tmapIDs[0], newPayload: replacement.payload(of: replacement.tmapIDs[0]))
                changedRoots.insert(original.tmapIDs[0])
            }
        }
        for thumbnail in original.references.filter({ $0.type == "thmb" && $0.to.contains(original.primary) }).map(\.from) {
            guard let dimensions = original.dimensions(of: thumbnail) else { throw CardError.imageValidation }
            let resized = stored.transformed(by: CGAffineTransform(scaleX: CGFloat(dimensions.width) / stored.extent.width,
                y: CGFloat(dimensions.height) / stored.extent.height))
            let thumbnailURL = work.appendingPathComponent("thumbnail-\(thumbnail).heic")
            try context.writeHEIFRepresentation(of: resized, to: thumbnailURL, format: .RGBA8,
                colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!, options: [:])
            let encoded = try HeifContainer.load(fileURL: thumbnailURL)
            try container.replaceImage(thumbnail, from: encoded, root: encoded.primary)
            changedRoots.insert(thumbnail)
        }
        try container.write(to: destination)
        try verifyPreservation(original: original, output: HeifContainer.load(fileURL: destination), changedRoots: changedRoots)
        guard let result = CGImageSourceCreateWithURL(destination as CFURL, nil),
              CGImageSourceCreateImageAtIndex(result, 0, nil) != nil else { throw CardError.imageValidation }
        try PhotoAuxiliaryData.verify(source: source, output: result)
    }

    private static func verifyPreservation(original: HeifContainer, output: HeifContainer, changedRoots: Set<UInt32>) throws {
        for item in original.items where !changedRoots.contains(item.id) {
            guard let retained = output.item(item.id), retained.type == item.type,
                  try output.payload(of: item.id) == original.payload(of: item.id) else { throw CardError.auxiliaryEncoding }
            let before = original.associations(of: item.id).map { original.properties[$0.index - 1] }
            let after = output.associations(of: item.id).map { output.properties[$0.index - 1] }
            guard before == after else { throw CardError.auxiliaryEncoding }
        }
        for reference in original.references where !(reference.type == "dimg" && changedRoots.contains(reference.from)) {
            guard output.references.contains(reference) else { throw CardError.auxiliaryEncoding }
        }
    }

    private static func gainMapWithCard(_ original: [String: Any], card: PhotoCard, displaySize: CGSize,
                                        storedSize: CGSize, orientation: CGImagePropertyOrientation) throws -> [String: Any] {
        guard !card.rows.isEmpty else { return original }
        guard let description = original[kCGImageAuxiliaryDataInfoDataDescription as String] as? [String: Any],
              let width = description["Width"] as? Int, let height = description["Height"] as? Int,
              let stride = description["BytesPerRow"] as? Int,
              (description["PixelFormat"] as? NSNumber)?.uint32Value == kCVPixelFormatType_OneComponent8,
              var bytes = original[kCGImageAuxiliaryDataInfoData as String] as? Data,
              width > 0, height > 0, width <= 20_000, height <= 20_000, stride >= width,
              stride <= bytes.count / height else { throw CardError.auxiliaryEncoding }
        let layout = try CardLayout(size: displaySize, card: card)
        let rect = CGRect(x: layout.rect.minX, y: displaySize.height - layout.rect.maxY,
                          width: layout.rect.width, height: layout.rect.height)
        let radius = layout.radius
        let sampleSize = max(storedSize.width / CGFloat(width), storedSize.height / CGFloat(height))
        for row in 0..<height {
            for column in 0..<width {
                let x = (CGFloat(column) + 0.5) / CGFloat(width) * storedSize.width
                let y = (CGFloat(row) + 0.5) / CGFloat(height) * storedSize.height
                let point: CGPoint
                switch orientation {
                case .up: point = CGPoint(x: x, y: y)
                case .upMirrored: point = CGPoint(x: storedSize.width - x, y: y)
                case .down: point = CGPoint(x: storedSize.width - x, y: storedSize.height - y)
                case .downMirrored: point = CGPoint(x: x, y: storedSize.height - y)
                case .leftMirrored: point = CGPoint(x: y, y: x)
                case .right: point = CGPoint(x: storedSize.height - y, y: x)
                case .rightMirrored: point = CGPoint(x: storedSize.height - y, y: storedSize.width - x)
                case .left: point = CGPoint(x: y, y: storedSize.width - x)
                @unknown default: throw CardError.auxiliaryEncoding
                }
                let qx = abs(point.x - rect.midX) - (rect.width / 2 - radius)
                let qy = abs(point.y - rect.midY) - (rect.height / 2 - radius)
                let distance = hypot(max(qx, 0), max(qy, 0)) + min(max(qx, qy), 0) - radius
                let coverage = min(1, max(0, 0.5 - distance / sampleSize))
                if coverage > 0 { bytes[row * stride + column] = UInt8((Double(bytes[row * stride + column]) * (1 - coverage)).rounded()) }
            }
        }
        var result = original
        result[kCGImageAuxiliaryDataInfoData as String] = bytes
        return result
    }
}

private extension Array {
    nonisolated func single(where predicate: (Element) -> Bool) -> Element? {
        let matches = filter(predicate)
        return matches.count == 1 ? matches[0] : nil
    }
}
