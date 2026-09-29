import Foundation
import CoreImage
import ImageIO
import UniformTypeIdentifiers

nonisolated enum StyleInjectionError: Error {
    /// The file already carries a styles stack; injection is skipped by design.
    case alreadyStyled
    case unsupportedSource(String)
    case encodingFailed(String)
}

/// Injects the Apple Photographic Styles 2/3 layer into a HEIC container. HEIC sources
/// are surgically extended without re-encoding a single payload; raster sources are
/// converted to HEIC first (keeping the gain map for HDR inputs). The two style
/// generations are independent: a photo with only the 2023 Standard stack can still
/// gain the 2026 texture layer, and the other way round.
nonisolated enum StyleInjection {
    static func stylesCoverage(in url: URL) -> (photographic: Bool, texture: Bool) {
        guard let container = try? HeifContainer.load(fileURL: url) else { return (false, false) }
        return container.stylesCoverage
    }

    static func inject(source: URL, kind: PhotoMediaKind, hdr: Bool, addPhotographic: Bool,
                       addTexture: Bool, grainSeedName: String, destination: URL) throws {
        let convertible: [PhotoMediaKind] = [.stillJPEG, .ultraHDRJPEG, .stillPNG]
        let direct: [PhotoMediaKind] = [.stillHEIC, .heicWithAuxiliaryData]
        guard direct.contains(kind) || convertible.contains(kind) else {
            if kind == .motionJPEG || kind == .hdrMotionJPEG {
                throw StyleInjectionError.unsupportedSource("embedded motion video would be lost")
            }
            if kind == .xiaomiPortraitJPEG {
                throw StyleInjectionError.unsupportedSource("xiaomi depth structure needs the android converter")
            }
            throw StyleInjectionError.unsupportedSource("source format \(kind)")
        }
        guard addPhotographic || addTexture else {
            throw StyleInjectionError.encodingFailed("no styles layer requested")
        }

        var base = source
        var converted: URL?
        if convertible.contains(kind) {
            let target = FileManager.default.temporaryDirectory
                .appendingPathComponent("fuyao-style-base-\(UUID().uuidString).heic")
            try convertToHEIC(source: source, hdr: hdr, destination: target)
            base = target
            converted = target
        }
        defer { if let converted { try? FileManager.default.removeItem(at: converted) } }

        var container: HeifContainer
        do {
            container = try HeifContainer.load(fileURL: base)
        } catch let error as MediaContainerError {
            throw StyleInjectionError.encodingFailed("container parse: \(error)")
        }
        guard !container.compatibleBrands.contains("avif"), container.majorBrand != "avif" else {
            throw StyleInjectionError.unsupportedSource("avif container")
        }
        let coverage = container.stylesCoverage
        if addPhotographic && coverage.photographic { throw StyleInjectionError.alreadyStyled }
        if addTexture && coverage.texture { throw StyleInjectionError.alreadyStyled }
        // The native 2026 contract keeps the 2023 styles item alongside the texture layer.
        let applyPhotographic = addPhotographic || (addTexture && !coverage.photographic)
        let applyTexture = addTexture

        let dimensions = try primaryDimensions(container)
        let landscape = dimensions.width >= dimensions.height
        let maxWidth = landscape ? 2880 : 2560
        let maxHeight = landscape ? 2560 : 2880
        let scale = min(1.0, Double(maxWidth) / Double(dimensions.width), Double(maxHeight) / Double(dimensions.height))
        func fitted(_ value: Int) -> Int {
            let even = max(1, Int((Double(value) * scale / 2.0).rounded())) * 2
            return min(even, value == dimensions.width ? maxWidth : maxHeight)
        }
        // Every black placeholder uses the golden monochrome frame: VideoToolbox cannot
        // encode true 4:0:0, and a 4:2:0 Main stream behind a monochrome hvcC
        // declaration crashes the Photos style editor (device report) even though
        // ImageIO decodes it. The golden frame is Rext monochrome end to end.
        let blackPlaceholder = HevcAuxStill.EncodedStill(
            payload: AppleStyleGolden.textureMattePayload,
            properties: [HeifContainer.ispeBox(AppleTextureStyles.matteWidth, AppleTextureStyles.matteHeight),
                         HeifContainer.pixiBox(channels: [8]),
                         AppleStyleGolden.textureMatteHvcc])
        let matte: HevcAuxStill.EncodedStill? = applyTexture ? blackPlaceholder : nil
        // The style aux items live in the primary's stored frame; scene data and the
        // thumbnail are un-rotated by the primary's own irot/imir before encoding.
        let orientation = primaryOrientation(container)
        if applyPhotographic {
            let scene: (sample: AppleStyleMetadata.SceneSample, thumbnail: HevcAuxStill.EncodedStill)
            do {
                scene = try HevcAuxStill.styleSceneSample(source: source, angle: orientation.angle,
                                                          mirror: orientation.mirror)
            } catch let error as HevcAuxStillError {
                throw StyleInjectionError.encodingFailed("\(error)")
            }
            applyPhotographicStyles(&container, deltaWidth: fitted(dimensions.width),
                                    deltaHeight: fitted(dimensions.height),
                                    landscape: landscape, linear: scene.thumbnail, sky: blackPlaceholder,
                                    scene: scene.sample, shared: orientation.shared)
        }
        if applyTexture, let matte {
            applyTextureStyles(&container, textureInfo: AppleTextureStyles.textureInfoPayload(
                grainSeed: AppleTextureStyles.grainSeedFor(grainSeedName)), matte: matte)
        }
        // A photo that already carries a (native) 2023 style stack keeps its MakerNote
        // and styles payload byte-identical when only the texture layer is added, the
        // add-texture contract of nathanatgit/Shalielie: tag 84's runtime plist stays
        // consistent with the photo's real style item. The one exception is the person
        // mask hint below, which the blank mattes make unsafe.
        if applyPhotographic {
            try applyStyleMakerNote(&container, styleIdentifier: AppleStyleMetadata.newIdentifier())
        }
        if !applyPhotographic, applyTexture {
            try neutralizePersonMasksHint(&container)
        }

        do {
            try container.write(to: destination)
        } catch let error as MediaContainerError {
            throw StyleInjectionError.encodingFailed("container write: \(error)")
        }
        try verify(destination: destination, before: coverage, appliedPhotographic: applyPhotographic,
                   appliedTexture: applyTexture, width: dimensions.width, height: dimensions.height)
    }

    /// Faithful JPEG/PNG to HEIC conversion for the injection base: the gain map rides
    /// the ImageIO ISO gain-map request and the EXIF/TIFF/GPS dictionaries carry over.
    private static func convertToHEIC(source: URL, hdr: Bool, destination: URL) throws {
        guard let cgSource = CGImageSourceCreateWithURL(source as CFURL, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(cgSource, 0, nil) as? [String: Any],
              let image = CIImage(contentsOf: source, options: [.applyOrientationProperty: true,
                                                                .expandToHDR: hdr, .toneMapHDRtoSDR: !hdr]) else {
            throw StyleInjectionError.encodingFailed("conversion decode")
        }
        let context = CIContext(options: [.cacheIntermediates: false])
        guard let rendered = context.createCGImage(image, from: image.extent, format: hdr ? .RGBAh : .RGBA8,
                                                   colorSpace: CGColorSpace(name: hdr ? CGColorSpace.extendedLinearDisplayP3
                                                                       : CGColorSpace.displayP3)!,
                                                   deferred: false, calculateHDRStats: hdr) else {
            throw StyleInjectionError.encodingFailed("conversion render")
        }
        guard let output = CGImageDestinationCreateWithURL(destination as CFURL, UTType.heic.identifier as CFString, 1, nil) else {
            throw StyleInjectionError.encodingFailed("conversion destination")
        }
        var metadata: [String: Any] = [kCGImagePropertyOrientation as String: 1]
        for key in [kCGImagePropertyExifDictionary, kCGImagePropertyTIFFDictionary, kCGImagePropertyGPSDictionary] {
            if let dict = properties[key as String] { metadata[key as String] = dict }
        }
        if let maker = properties[kCGImagePropertyMakerAppleDictionary as String] as? [String: Any],
           let pair = maker["17"] {
            metadata[kCGImagePropertyMakerAppleDictionary as String] = ["17": pair]
        }
        metadata[kCGImageDestinationLossyCompressionQuality as String] = 1.0
        if hdr { metadata[kCGImageDestinationEncodeRequest as String] = kCGImageDestinationEncodeToISOGainmap }
        CGImageDestinationAddImage(output, rendered, metadata as CFDictionary)
        guard CGImageDestinationFinalize(output) else {
            throw StyleInjectionError.encodingFailed("conversion finalize")
        }
    }

    private static func primaryDimensions(_ container: HeifContainer) throws -> (width: Int, height: Int) {
        for association in container.associations(of: container.primary) {
            guard let type = container.propertyType(association.index), type == "ispe" else { continue }
            let raw = container.properties[association.index - 1]
            guard raw.count >= 20 else { break }
            let width = Int(u32BE(raw, 12))
            let height = Int(u32BE(raw, 16))
            guard width > 0, height > 0 else { break }
            return (width, height)
        }
        throw StyleInjectionError.unsupportedSource("primary ispe missing")
    }

    private static func applyPhotographicStyles(_ container: inout HeifContainer, deltaWidth: Int, deltaHeight: Int,
                                                landscape: Bool, linear: HevcAuxStill.EncodedStill,
                                                sky: HevcAuxStill.EncodedStill, scene: AppleStyleMetadata.SceneSample,
                                                shared: [HeifContainer.PropertyAssociation]) {
        let toneTargets = container.toneTargets
        let primaryAssociations = container.associations(of: container.primary)
        let colrIndex = primaryAssociations.first { container.propertyType($0.index) == "colr" }?.index

        let ispe512 = container.appendProperty(HeifContainer.ispeBox(512, 512))
        let deltaHvcc = container.appendProperty(AppleStyleGolden.DELTA_HVCC)
        var tileIDs: [UInt32] = []
        for _ in 0..<30 {
            var tileProperties: [HeifContainer.PropertyAssociation] = [.init(index: ispe512, essential: true)]
            if let colrIndex { tileProperties.append(.init(index: colrIndex, essential: true)) }
            tileProperties.append(.init(index: deltaHvcc, essential: true))
            tileIDs.append(container.addHiddenItem(type: "hvc1", infoSuffix: [0],
                                                   payload: AppleStyleGolden.DELTA_TILE, properties: tileProperties))
        }

        let ispeDelta = container.appendProperty(HeifContainer.ispeBox(deltaWidth, deltaHeight))
        let pixiDelta = container.appendProperty(HeifContainer.pixiBox(channels: [10, 10, 10]))
        let auxDelta = container.appendProperty(HeifContainer.auxCBox(AppleStyleMetadata.deltaMapURN))
        var gridProperties: [HeifContainer.PropertyAssociation] = []
        if let colrIndex { gridProperties.append(.init(index: colrIndex, essential: true)) }
        gridProperties.append(.init(index: ispeDelta, essential: false))
        gridProperties.append(.init(index: pixiDelta, essential: false))
        gridProperties.append(.init(index: auxDelta, essential: true))
        gridProperties.append(contentsOf: shared)
        let rows = landscape ? 5 : 6
        let columns = landscape ? 6 : 5
        var gridPayload: [UInt8] = [0, 0, UInt8(rows - 1), UInt8(columns - 1)]
        gridPayload.append(UInt8((deltaWidth >> 8) & 0xff)); gridPayload.append(UInt8(deltaWidth & 0xff))
        gridPayload.append(UInt8((deltaHeight >> 8) & 0xff)); gridPayload.append(UInt8(deltaHeight & 0xff))
        let gridID = container.addHiddenItem(type: "grid", infoSuffix: [0], payload: gridPayload,
                                             properties: gridProperties)
        container.addReference(type: "dimg", from: gridID, to: tileIDs)
        container.addReference(type: "auxl", from: gridID, to: toneTargets)

        attachEncoded(&container, linear, urn: AppleStyleMetadata.linearThumbnailURN, xmp: nil,
                      toneTargets: toneTargets, shared: shared)
        attachEncoded(&container, sky, urn: AppleStyleMetadata.skyMatteURN, xmp: AppleStyleMetadata.skyMatteXMP,
                      toneTargets: toneTargets, shared: shared)

        let styleID = container.addHiddenItem(
            type: "uri ",
            infoSuffix: Array("metadata\0\(AppleStyleMetadata.stylesContentType)\0".utf8),
            payload: AppleStyleMetadata.styleMetadata(scene: scene), properties: [])
        container.addReference(type: "cdsc", from: styleID, to: toneTargets)
    }

    // The blank mattes stay at the device-validated property shape without irot: they
    // carry no orientation information of their own, and unlike native mattes their
    // content never needs to register with the photo.
    private static func applyTextureStyles(_ container: inout HeifContainer, textureInfo: [UInt8],
                                           matte: HevcAuxStill.EncodedStill) {
        let toneTargets = container.toneTargets
        let ispe = container.appendProperty(HeifContainer.ispeBox(AppleTextureStyles.matteWidth,
                                                                  AppleTextureStyles.matteHeight))
        let pixi = container.appendProperty(HeifContainer.pixiBox(channels: [8]))
        let hvcc = container.appendProperty(matte.properties.first { propertyHasType($0, "hvcC") } ?? [])
        for urn in AppleTextureStyles.semanticMatteURNS {
            let auxC = container.appendProperty(HeifContainer.auxCBox(urn))
            let id = container.addHiddenItem(type: "hvc1", infoSuffix: [0], payload: matte.payload, properties: [
                .init(index: ispe, essential: false), .init(index: pixi, essential: false),
                .init(index: auxC, essential: true), .init(index: hvcc, essential: true)])
            container.addReference(type: "auxl", from: id, to: toneTargets)
        }
        let textureID = container.addHiddenItem(
            type: "uri ",
            infoSuffix: Array("metadata\0\(AppleTextureStyles.textureStylesContentType)\0".utf8),
            payload: textureInfo, properties: [])
        container.addReference(type: "cdsc", from: textureID, to: toneTargets)
    }

    private static func attachEncoded(_ container: inout HeifContainer, _ still: HevcAuxStill.EncodedStill,
                                      urn: String, xmp: String?, toneTargets: [UInt32],
                                      shared: [HeifContainer.PropertyAssociation]) {
        var imported: [HeifContainer.PropertyAssociation] = still.properties.map {
            .init(index: container.appendProperty($0), essential: false)
        }
        imported.append(.init(index: container.appendProperty(HeifContainer.auxCBox(urn)), essential: true))
        imported.append(contentsOf: shared)
        let encodedID = container.addHiddenItem(type: "hvc1", infoSuffix: [0], payload: still.payload,
                                                properties: imported)
        container.addReference(type: "auxl", from: encodedID, to: toneTargets)
        if let xmp {
            let sidecarID = container.addHiddenItem(type: "mime",
                                                    infoSuffix: Array("\0application/rdf+xml\0\0".utf8),
                                                    payload: Array(xmp.utf8), properties: [])
            container.addReference(type: "cdsc", from: sidecarID, to: [encodedID])
        }
    }

    /// The primary's irot/imir as display angle/mirror plus the property associations
    /// every style aux item shares, so the whole style graph renders in one frame.
    private static func primaryOrientation(_ container: HeifContainer)
        -> (angle: Int, mirror: Int?, shared: [HeifContainer.PropertyAssociation]) {
        var angle = 0
        var mirror: Int?
        var shared: [HeifContainer.PropertyAssociation] = []
        for association in container.associations(of: container.primary) {
            guard let type = container.propertyType(association.index),
                  let raw = container.properties.indices.contains(association.index - 1)
                      ? container.properties[association.index - 1] : nil, raw.count >= 9 else { continue }
            if type == "irot" {
                angle = Int(raw[8] & 3) * 90
                shared.append(.init(index: association.index, essential: true))
            }
            if type == "imir" {
                mirror = Int(raw[8] & 1)
                shared.append(.init(index: association.index, essential: true))
            }
        }
        return (angle, mirror, shared)
    }

    /// Flips the existing styles item's PersonMasksValidHint to -1.0: the blank 2026
    /// mattes this injector adds cannot back a "valid" announcement, and Photos' style
    /// V2 pipeline aborts on the combination (device-verified editor crash on both
    /// platforms). Length-preserving, so every other payload byte stays identical.
    private static func neutralizePersonMasksHint(_ container: inout HeifContainer) throws {
        guard let styleItem = container.items.first(where: { item in
            item.type == "uri " && container.contentType(of: item) == AppleStyleMetadata.stylesContentType
        }) else { return }
        let payload = (try? container.payload(of: styleItem.id)) ?? []
        let patched: [UInt8]
        do {
            patched = try AppleStyleMetadata.neutralizedPersonMasksHint(payload)
        } catch let error as AppleStylePayloadError {
            throw StyleInjectionError.encodingFailed("\(error)")
        }
        if patched != payload {
            try container.rehomeItemPayload(id: styleItem.id, newPayload: patched)
        }
    }

    /// The style MakerNote rides the merged Apple MakerNote; native captures keep their
    /// captured tags (notably the tag 17 Live Photo pairing id).
    private static func applyStyleMakerNote(_ container: inout HeifContainer, styleIdentifier: String) throws {
        if let exifItem = container.items.first(where: { $0.type == "Exif" }) {
            let app1 = AppleStyleMetadata.exifApp1Payload((try? container.payload(of: exifItem.id)) ?? [])
            let merged = try AppleStyleMetadata.appleNoteWithStyle(exif: app1, styleIdentifier: styleIdentifier)
            try container.rehomeItemPayload(id: exifItem.id, newPayload: [0, 0, 0, 6] + Array(merged))
        } else {
            let built = try AppleStyleMetadata.appleNoteWithStyle(exif: nil, styleIdentifier: styleIdentifier)
            let id = container.addHiddenItem(type: "Exif", infoSuffix: [0],
                                             payload: [0, 0, 0, 6] + Array(built), properties: [])
            container.addReference(type: "cdsc", from: id, to: [container.primary])
        }
    }

    private static func propertyHasType(_ raw: [UInt8], _ type: String) -> Bool {
        raw.count >= 8 && String(decoding: raw[4..<8], as: UTF8.self) == type
    }

    private static func verify(destination: URL, before: (photographic: Bool, texture: Bool),
                               appliedPhotographic: Bool, appliedTexture: Bool,
                               width: Int, height: Int) throws {
        let reloaded: HeifContainer
        do {
            reloaded = try HeifContainer.load(fileURL: destination)
        } catch let error as MediaContainerError {
            throw StyleInjectionError.encodingFailed("verify parse: \(error)")
        }
        let coverage = reloaded.stylesCoverage
        guard coverage.photographic == (before.photographic || appliedPhotographic) else {
            throw StyleInjectionError.encodingFailed("styles item missing after write")
        }
        guard coverage.texture == (before.texture || appliedTexture) else {
            throw StyleInjectionError.encodingFailed("texture item missing after write")
        }
        if appliedPhotographic {
            guard let grid = reloaded.items.first(where: { reloaded.auxCURN(of: $0.id) == AppleStyleMetadata.deltaMapURN }),
                  reloaded.references.first(where: { $0.type == "dimg" && $0.from == grid.id })?.to.count == 30,
                  reloaded.references.first(where: { $0.type == "auxl" && $0.from == grid.id })?.to == reloaded.toneTargets else {
                throw StyleInjectionError.encodingFailed("delta map layer incomplete")
            }
        }
        if appliedTexture {
            let mattes = AppleTextureStyles.semanticMatteURNS.filter { urn in
                reloaded.items.contains { reloaded.auxCURN(of: $0.id) == urn }
            }
            guard mattes.count == 12 else { throw StyleInjectionError.encodingFailed("matte layer incomplete") }
        }
        guard let output = CGImageSourceCreateWithURL(destination as CFURL, nil),
              CGImageSourceGetCount(output) == 1,
              let properties = CGImageSourceCopyPropertiesAtIndex(output, 0, nil) as? [String: Any],
              (properties[kCGImagePropertyPixelWidth as String] as? NSNumber)?.intValue == width,
              (properties[kCGImagePropertyPixelHeight as String] as? NSNumber)?.intValue == height,
              CGImageSourceCreateThumbnailAtIndex(output, 0, [kCGImageSourceCreateThumbnailFromImageAlways: true]
                  as CFDictionary) != nil else {
            throw StyleInjectionError.encodingFailed("verify decode")
        }
    }
}

nonisolated private func u32BE(_ bytes: [UInt8], _ at: Int) -> UInt32 {
    (UInt32(bytes[at]) << 24) | (UInt32(bytes[at + 1]) << 16) | (UInt32(bytes[at + 2]) << 8) | UInt32(bytes[at + 3])
}
