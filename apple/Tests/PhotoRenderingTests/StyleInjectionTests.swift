import Testing
import Foundation
import CoreImage
import ImageIO
@testable import PhotoRenderingCore

struct StyleInjectionTests {
    @Test func plainHEICGainsTheFullStylesLayer() throws {
        let folder = try temporaryFolder()
        let source = folder.appendingPathComponent("source.heic")
        try writeSDRHEIC(to: source, width: 512, height: 384, hdr: false)
        let output = folder.appendingPathComponent("styled.heic")

        try StyleInjection.inject(source: source, kind: .stillHEIC, hdr: false, addPhotographic: true,
                                  addTexture: false, grainSeedName: "IMG_8565.HEIC", destination: output)

        let container = try HeifContainer.load(fileURL: output)
        let coverage = container.stylesCoverage
        #expect(coverage.photographic && !coverage.texture)
        let styleItem = try #require(container.items.first { $0.type == "uri " })
        #expect(String(decoding: styleItem.infoSuffix, as: UTF8.self).hasPrefix("metadata\0tag:apple.com,2023:photo:metadata:styles\0"))
        #expect(try container.payload(of: styleItem.id).count > 51840)

        let grid = try #require(container.items.first { container.auxCURN(of: $0.id) == AppleStyleMetadata.deltaMapURN })
        let gridPayload = try container.payload(of: grid.id)
        // Landscape 512x384: 5 rows x 6 columns; the fitted delta size never upscales.
        #expect(gridPayload == [0, 0, 4, 5, 0x02, 0x00, 0x01, 0x80]) // 512, 384
        let tiles = try #require(container.references.first { $0.type == "dimg" && $0.from == grid.id }).to
        #expect(tiles.count == 30)
        for tile in tiles {
            let item = try #require(container.item(tile))
            #expect(item.type == "hvc1")
            #expect(try container.payload(of: tile) == AppleStyleGolden.DELTA_TILE)
        }
        #expect(container.references.first { $0.type == "auxl" && $0.from == grid.id }?.to == [container.primary])
        let linear = try #require(container.items.first { container.auxCURN(of: $0.id) == AppleStyleMetadata.linearThumbnailURN })
        #expect(try container.payload(of: linear.id).count > 0)
        let sky = try #require(container.items.first { container.auxCURN(of: $0.id) == AppleStyleMetadata.skyMatteURN })
        #expect(container.references.contains { $0.type == "auxl" && $0.from == sky.id })
        #expect(container.items.contains { $0.type == "mime" })
        #expect(container.references.contains { $0.type == "cdsc" && $0.to == [sky.id] })

        // Fresh Exif item with the style MakerNote, referenced from the primary.
        let exifItem = try #require(container.items.first { $0.type == "Exif" })
        #expect(container.references.contains { $0.type == "cdsc" && $0.from == exifItem.id && $0.to == [container.primary] })
        let app1 = try container.payload(of: exifItem.id)
        #expect(Array(app1[0..<4]) == [0, 0, 0, 6])
        #expect(contains(app1, Array("Apple iOS".utf8)))

        try verifyDecodable(output, width: 512, height: 384)
    }

    @Test func textureLayerAddsTwelveMattesAndThe2026Item() throws {
        let folder = try temporaryFolder()
        let source = folder.appendingPathComponent("portrait.heic")
        try writeSDRHEIC(to: source, width: 384, height: 512, hdr: false)
        let output = folder.appendingPathComponent("styled3.heic")

        do {
            try StyleInjection.inject(source: source, kind: .stillHEIC, hdr: false, addPhotographic: true,
                                      addTexture: true, grainSeedName: "IMG_1234.jpg", destination: output)
        } catch {
            if FileManager.default.fileExists(atPath: output.path) {
                    }
            throw error
        }

        let container = try HeifContainer.load(fileURL: output)
        #expect(container.stylesCoverage.texture)
        #expect(container.items.filter { $0.type == "uri " }.count == 2)
        let texture = try #require(container.items.first {
            $0.type == "uri " && contains(Array($0.infoSuffix), Array(AppleTextureStyles.textureStylesContentType.utf8))
        })
        #expect(String(decoding: texture.infoSuffix, as: UTF8.self).hasPrefix("metadata\0"))
        #expect(container.references.first { $0.type == "cdsc" && $0.from == texture.id }?.to == container.toneTargets)

        let matteItems = container.items.filter { item in
            guard let urn = container.auxCURN(of: item.id) else { return false }
            return AppleTextureStyles.semanticMatteURNS.contains(urn)
        }
        #expect(matteItems.count == 12)
        let payloads = Set(try matteItems.map { try container.payload(of: $0.id) })
        #expect(payloads.count == 1)
        for item in matteItems {
            #expect(item.type == "hvc1")
            let ispe = try #require(container.associations(of: item.id).map { container.properties[$0.index - 1] }
                .first { String(decoding: $0[4..<8], as: UTF8.self) == "ispe" })
            #expect(readU32(ispe, 12) == AppleTextureStyles.matteWidth)
            #expect(readU32(ispe, 16) == AppleTextureStyles.matteHeight)
            #expect(container.references.first { $0.type == "auxl" && $0.from == item.id }?.to == container.toneTargets)
        }
        try verifyDecodable(output, width: 384, height: 512)
    }

    @Test func styledSourcesAreRejectedAndHDRSourcesKeepTheirToneMap() throws {
        let folder = try temporaryFolder()
        let source = folder.appendingPathComponent("hdr.heic")
        try writeSDRHEIC(to: source, width: 256, height: 256, hdr: true)
        let container = try HeifContainer.load(fileURL: source)
        #expect(!container.tmapIDs.isEmpty)

        let output = folder.appendingPathComponent("hdr-styled.heic")
        try StyleInjection.inject(source: source, kind: .heicWithAuxiliaryData, hdr: true, addPhotographic: true,
                                  addTexture: false, grainSeedName: "IMG_1.jpg", destination: output)
        let styled = try HeifContainer.load(fileURL: output)
        let toneTargets = [styled.primary] + styled.tmapIDs
        let grid = try #require(styled.items.first { styled.auxCURN(of: $0.id) == AppleStyleMetadata.deltaMapURN })
        #expect(styled.references.first { $0.type == "auxl" && $0.from == grid.id }?.to == toneTargets)
        let styleItem = try #require(styled.items.first { $0.type == "uri " })
        #expect(styled.references.first { $0.type == "cdsc" && $0.from == styleItem.id }?.to == toneTargets)

        do {
            try StyleInjection.inject(source: output, kind: .heicWithAuxiliaryData, hdr: true, addPhotographic: true,
                                      addTexture: false, grainSeedName: "IMG_1.jpg", destination: folder.appendingPathComponent("again.heic"))
            Issue.record("injection should refuse a styled file")
        } catch let error as StyleInjectionError {
            guard case .alreadyStyled = error else { Issue.record("\(error)"); return }
        }
    }

    @Test func photographicOnlySourceGainsTheTextureLayer() throws {
        let folder = try temporaryFolder()
        let source = folder.appendingPathComponent("source.heic")
        try writeSDRHEIC(to: source, width: 256, height: 192, hdr: false)
        let standard = folder.appendingPathComponent("standard.heic")
        try StyleInjection.inject(source: source, kind: .stillHEIC, hdr: false, addPhotographic: true,
                                  addTexture: false, grainSeedName: "IMG_1.jpg", destination: standard)
        var coverage = StyleInjection.stylesCoverage(in: standard)
        #expect(coverage.photographic && !coverage.texture)

        let output = folder.appendingPathComponent("textured.heic")
        try StyleInjection.inject(source: standard, kind: .heicWithAuxiliaryData, hdr: false, addPhotographic: false,
                                  addTexture: true, grainSeedName: "IMG_2.jpg", destination: output)
        coverage = StyleInjection.stylesCoverage(in: output)
        #expect(coverage.photographic && coverage.texture)
        let container = try HeifContainer.load(fileURL: output)
        #expect(container.items.filter { $0.type == "uri " }.count == 2)
        let mattes = AppleTextureStyles.semanticMatteURNS.filter { urn in
            container.items.contains { container.auxCURN(of: $0.id) == urn }
        }
        #expect(mattes.count == 12)
        try verifyDecodable(output, width: 256, height: 192)

        do {
            try StyleInjection.inject(source: output, kind: .heicWithAuxiliaryData, hdr: false, addPhotographic: false,
                                      addTexture: true, grainSeedName: "IMG_2.jpg", destination: folder.appendingPathComponent("again.heic"))
            Issue.record("texture injection should refuse a textured file")
        } catch let error as StyleInjectionError {
            guard case .alreadyStyled = error else { Issue.record("\(error)"); return }
        }
    }

    @Test func jpegSourcesConvertThenInject() throws {
        let folder = try temporaryFolder()
        let source = folder.appendingPathComponent("source.jpg")
        let base = CIImage(color: CIColor(red: 0.7, green: 0.4, blue: 0.2)).cropped(to: CGRect(x: 0, y: 0, width: 300, height: 400))
        try CIContext().writeJPEGRepresentation(of: base, to: source, colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!)
        let output = folder.appendingPathComponent("converted.heic")

        try StyleInjection.inject(source: source, kind: .stillJPEG, hdr: false, addPhotographic: true,
                                  addTexture: false, grainSeedName: "IMG_2.jpg", destination: output)
        try verifyDecodable(output, width: 300, height: 400)
        let container = try HeifContainer.load(fileURL: output)
        #expect(container.stylesCoverage.photographic)
        // EXIF-free source still produces an Exif item carrying the style MakerNote.
        #expect(container.items.contains { $0.type == "Exif" })
    }

    @Test func motionSourcesAreRefusedWithAReason() throws {
        let folder = try temporaryFolder()
        do {
            try StyleInjection.inject(source: folder.appendingPathComponent("missing.jpg"), kind: .motionJPEG, hdr: false,
                                      addPhotographic: true, addTexture: false, grainSeedName: "x",
                                      destination: folder.appendingPathComponent("out.heic"))
            Issue.record("motion source should be refused")
        } catch let error as StyleInjectionError {
            guard case .unsupportedSource = error else { Issue.record("\(error)"); return }
        }
    }

    @Test func goldenMatteDeclaresTrueMonochrome() throws {
        // The texture placeholders ship as Rext-monochrome golden bytes: a 4:2:0 Main
        // stream behind a monochrome declaration crashed the Photos style editor.
        let hvcC = AppleStyleGolden.textureMatteHvcc
        #expect(hvcC.count == 113)
        #expect(hvcC[8] == 1) // configurationVersion
        #expect(hvcC[9] & 0x1f == 4) // general_profile_idc = 4 (Rext, mono capable)
        #expect(hvcC[20] == 0x5a) // general_level_idc
        #expect(hvcC[24] & 3 == 0) // chroma_format_idc = monochrome
        #expect(hvcC[25] & 7 == 0) // 8-bit
        // The payload is one length-prefixed IDR slice; its own SPS must agree.
        let sliceLength = Int(AppleStyleGolden.textureMattePayload[0]) << 24
            | Int(AppleStyleGolden.textureMattePayload[1]) << 16
            | Int(AppleStyleGolden.textureMattePayload[2]) << 8 | Int(AppleStyleGolden.textureMattePayload[3])
        #expect(sliceLength == 108)
        #expect(AppleStyleGolden.textureMattePayload.count == 4 + sliceLength)
        let linear = try HevcAuxStill.styleSceneSample(source: makeColorJPEG(), angle: 0, mirror: nil).thumbnail
        let linearHvcc = try #require(linear.properties.first {
            $0.count >= 8 && String(decoding: $0[4..<8], as: UTF8.self) == "hvcC"
        })
        // The linear thumbnail keeps its 4:2:0 colour declaration.
        #expect(linearHvcc[24] & 3 == 1)
        let linearIspe = try #require(linear.properties.first {
            $0.count >= 8 && String(decoding: $0[4..<8], as: UTF8.self) == "ispe"
        })
        #expect(readU32(linearIspe, 12) == 1024)
        #expect(readU32(linearIspe, 16) == 768)
    }

    // MARK: Helpers

    private func temporaryFolder() throws -> URL {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder
    }

    private func writeSDRHEIC(to url: URL, width: Int, height: Int, hdr: Bool) throws {
        let bounds = CGRect(x: 0, y: 0, width: width, height: height)
        let base = CIImage(color: CIColor(red: 0.4, green: 0.3, blue: 0.5)).cropped(to: bounds)
        var options: [CIImageRepresentationOption: Any] = [:]
        if hdr {
            let hdrImage = base.applyingFilter("CIExposureAdjust", parameters: [kCIInputEVKey: 2]).settingContentHeadroom(4)
            options[.hdrImage] = hdrImage
        }
        try CIContext().writeHEIFRepresentation(of: base, to: url, format: .RGBA8,
                                                colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!, options: options)
    }

    private func makeColorJPEG() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("aux-\(UUID().uuidString).jpg")
        let base = CIImage(color: CIColor(red: 0.2, green: 0.6, blue: 0.9)).cropped(to: CGRect(x: 0, y: 0, width: 640, height: 480))
        try CIContext().writeJPEGRepresentation(of: base, to: url, colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!)
        return url
    }

    private func verifyDecodable(_ url: URL, width: Int, height: Int) throws {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil), CGImageSourceGetCount(source) == 1,
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [String: Any],
              (properties[kCGImagePropertyPixelWidth as String] as? NSNumber)?.intValue == width,
              (properties[kCGImagePropertyPixelHeight as String] as? NSNumber)?.intValue == height,
              CGImageSourceCreateThumbnailAtIndex(source, 0, [kCGImageSourceCreateThumbnailFromImageAlways: true] as CFDictionary) != nil else {
            Issue.record("output not decodable at \(width)x\(height)")
            return
        }
    }

    private func readU32(_ bytes: [UInt8], _ at: Int) -> Int {
        Int(bytes[at]) << 24 | Int(bytes[at + 1]) << 16 | Int(bytes[at + 2]) << 8 | Int(bytes[at + 3])
    }

    private func contains(_ haystack: [UInt8], _ needle: [UInt8]) -> Bool {
        guard needle.count <= haystack.count else { return false }
        for start in 0...(haystack.count - needle.count) {
            if Array(haystack[start..<start + needle.count]) == needle { return true }
        }
        return false
    }
}
