import Testing
import Foundation
import CoreImage
import ImageIO
import UniformTypeIdentifiers
@testable import PhotoRenderingCore

/// Scene statistics and light-map derivation follow the nathanatgit/Shalielie (MIT)
/// calibration: linear-light luma percentiles for key '6', and a 32x32 c/d grid read in
/// the stored orientation, rotated 180 degrees, floored at 0.040741.
struct StyleSceneSamplingTests {
    @Test func rasterOrientationOpsRoundTripThroughTheDisplayTransform() {
        let width = 3
        let height = 2
        let channels = 4
        let original = (0..<(width * height * channels)).map { UInt8($0 & 0xff) }
        for angle in [0, 90, 180, 270] {
            for mirror in [nil, 0, 1] {
                // Forward HEIF transform: rotate clockwise by irot, then apply imir.
                var displayed = original
                var quarterTurns = angle / 90
                var rasterWidth = width
                var rasterHeight = height
                while quarterTurns > 0 {
                    displayed = HevcAuxStill.rotateQuarter(displayed, width: rasterWidth, height: rasterHeight,
                                                           clockwise: true, channels: channels)
                    let swapped = rasterWidth
                    rasterWidth = rasterHeight
                    rasterHeight = swapped
                    quarterTurns -= 1
                }
                if let mirror {
                    displayed = HevcAuxStill.flip(displayed, width: rasterWidth, height: rasterHeight,
                                                  vertical: mirror == 1, channels: channels)
                }
                let stored = HevcAuxStill.unRotate(displayed, width: rasterWidth, height: rasterHeight,
                                                   angle: angle, mirror: mirror, channels: channels)
                #expect(stored == original, "angle \(angle) mirror \(String(describing: mirror))")
            }
        }
    }

    @Test func quarterTurnsFollowTheVisualConvention() {
        // Raster row 0 is the visual top: [[1,2,3],[4,5,6]] turned clockwise is [[4,1],[5,2],[6,3]].
        let raster: [UInt8] = [1, 2, 3, 4, 5, 6]
        let clockwise = HevcAuxStill.rotateQuarter(raster, width: 3, height: 2, clockwise: true, channels: 1)
        #expect(clockwise == [4, 1, 5, 2, 6, 3])
        let counter = HevcAuxStill.rotateQuarter(raster, width: 3, height: 2, clockwise: false, channels: 1)
        #expect(counter == [3, 6, 2, 5, 1, 4])
        let hflipped = HevcAuxStill.flip(raster, width: 3, height: 2, vertical: false, channels: 1)
        #expect(hflipped == [3, 2, 1, 6, 5, 4])
        let vflipped = HevcAuxStill.flip(raster, width: 3, height: 2, vertical: true, channels: 1)
        #expect(vflipped == [4, 5, 6, 1, 2, 3])
    }

    @Test func percentileInterpolatesOnSortedValues() {
        #expect(HevcAuxStill.percentile([], 0.5) == 0)
        #expect(HevcAuxStill.percentile([0, 1, 2, 3], 0) == 0)
        #expect(HevcAuxStill.percentile([0, 1, 2, 3], 1) == 3)
        #expect(HevcAuxStill.percentile([0, 1, 2, 3], 0.5) == 1.5)
        #expect(HevcAuxStill.percentile([0, 1, 2, 3], 0.25) == 0.75)
    }

    @Test func linearLumaUsesSRGBDecodeAndRec709Weights() {
        // BGRA raster: one pure-gray pixel, then one pure-red pixel (R sits at +2 in BGRA).
        let gray = UInt8(128)
        let raster: [UInt8] = [gray, gray, gray, 255, 0, 0, 255, 255]
        let luma = HevcAuxStill.linearLumaValues(raster, channelsBGRA: true)
        let expectedGray = HevcAuxStill.srgbToLinear(Double(gray) / 255.0)
        #expect(abs(luma[0] - expectedGray) < 1e-12)
        #expect(abs(luma[1] - 0.2126) < 1e-12)
    }

    @Test func fittedLightMapAppliesTheCalibratedFitsFloorAndFP16() {
        let floor = AppleStyleMetadata.SceneSample.lightMapFloor
        let values = [0.0, (floor - AppleStyleMetadata.SceneSample.cIntercept) / AppleStyleMetadata.SceneSample.cSlope / 2, 0.5]
        let c = AppleStyleMetadata.SceneSample.fittedLightMap(
            storedLinearLumaReversed: values, slope: AppleStyleMetadata.SceneSample.cSlope,
            intercept: AppleStyleMetadata.SceneSample.cIntercept)
        #expect(c.count == 6)
        func half(_ bytes: [UInt8], _ at: Int) -> Float16 {
            Float16(bitPattern: UInt16(bytes[at]) | (UInt16(bytes[at + 1]) << 8))
        }
        #expect(half(c, 0) == Float16(floor))
        #expect(abs(half(c, 4) - Float16(0.7774 * 0.5 + 0.0294)) < 0.001)
    }

    @Test func sceneSampleMeasuresAFlatImageAtItsLinearLuma() throws {
        let source = try writeJPEG(raster: flatRaster(gray: 128, width: 32, height: 32), width: 32, height: 32)
        let scene = try HevcAuxStill.styleSceneSample(source: source, angle: 0, mirror: nil).sample
        let flat = HevcAuxStill.srgbToLinear(128.0 / 255.0)
        #expect(abs(scene.blackPoint - flat) < 0.01)
        #expect(abs(scene.p50 - flat) < 0.01)
        #expect(abs(scene.whitePoint - flat) < 0.01)
        let firstC = fp16(scene.lightMapC, at: 0)
        #expect(abs(firstC - (AppleStyleMetadata.SceneSample.cSlope * flat + AppleStyleMetadata.SceneSample.cIntercept)) < 0.002)
        #expect(scene.lightMapC.count == 2048 && scene.lightMapD.count == 2048)
    }

    @Test func lightMapsReadTheStoredGridRotated180() throws {
        // Top rows black, bottom rows white: the reversed 32x32 grid starts with the
        // bright bottom rows and ends with the black top row.
        let width = 32
        let height = 32
        var raster = [UInt8]()
        for row in 0..<height {
            let level: UInt8 = row < height / 2 ? 0 : 255
            for _ in 0..<width { raster += [level, level, level, 255] }
        }
        let source = try writeJPEG(raster: raster, width: width, height: height)
        let scene = try HevcAuxStill.styleSceneSample(source: source, angle: 0, mirror: nil).sample
        let firstRowAverage = (0..<32).map { fp16(scene.lightMapC, at: $0 * 2) }.reduce(0, +) / 32
        let lastRowAverage = (0..<32).map { fp16(scene.lightMapC, at: (32 * 31 + $0) * 2) }.reduce(0, +) / 32
        let floorValue = Double(Float16(AppleStyleMetadata.SceneSample.lightMapFloor))
        #expect(firstRowAverage > 0.5)
        #expect(lastRowAverage == floorValue)

        // A 180-degree display turn must flip the map back: bright rows move to the end.
        let turned = try HevcAuxStill.styleSceneSample(source: source, angle: 180, mirror: nil).sample
        let turnedFirst = (0..<32).map { fp16(turned.lightMapC, at: $0 * 2) }.reduce(0, +) / 32
        let turnedLast = (0..<32).map { fp16(turned.lightMapC, at: (32 * 31 + $0) * 2) }.reduce(0, +) / 32
        #expect(turnedFirst == floorValue)
        #expect(turnedLast > 0.5)
    }

    @Test func storedThumbnailKeepsThe4By3ShapeAndSharedIrotOnRotatedSources() throws {
        let folder = try temporaryFolder()
        // A 240x320 stored HEIC carrying irot 90 (display 320x240), written through
        // ImageIO, which encodes the orientation property as a real irot box.
        let source = folder.appendingPathComponent("rotated.heic")
        let base = CIImage(color: CIColor(red: 0.3, green: 0.5, blue: 0.7))
            .cropped(to: CGRect(x: 0, y: 0, width: 240, height: 320))
        let context = CIContext()
        guard let cg = context.createCGImage(base, from: base.extent) else { throw CocoaError(.fileWriteUnknown) }
        let destination = CGImageDestinationCreateWithURL(source as CFURL, UTType.heic.identifier as CFString, 1, nil)!
        CGImageDestinationAddImage(destination, cg, [kCGImagePropertyOrientation as String: 6] as CFDictionary)
        CGImageDestinationFinalize(destination)

        let output = folder.appendingPathComponent("styled.heic")
        try StyleInjection.inject(source: source, kind: .stillHEIC, hdr: false, addPhotographic: true,
                                  addTexture: false, grainSeedName: "IMG_9.jpg", destination: output)
        let container = try HeifContainer.load(fileURL: output)
        #expect(container.associations(of: container.primary).contains {
            container.propertyType($0.index) == "irot"
        })
        guard let linear = container.items.first(where: { container.auxCURN(of: $0.id) == AppleStyleMetadata.linearThumbnailURN }) else {
            Issue.record("linear thumbnail item missing")
            return
        }
        let linearAssociations = container.associations(of: linear.id)
        #expect(linearAssociations.contains { container.propertyType($0.index) == "irot" })
        let ispe = try #require(linearAssociations.map { container.properties[$0.index - 1] }
            .first { String(decoding: $0[4..<8], as: UTF8.self) == "ispe" })
        #expect(readU32(ispe, 12) == 1024 && readU32(ispe, 16) == 768)
        // ImageIO reports the stored 240x320 geometry; the irot carries the display turn.
        try verifyDecodable(output, width: 240, height: 320)
    }

    @Test func halfFloatBitsMatchesTheFloat16API() {
        let values: [Float] = [0, 0.5, 1, 2, -0.5, 0.3115234375, 0.040741, 0.4181,
                               1.00048828125, 1.00146484375, 6.1e-5, 3.0e-5, 0.999, 65504]
        for value in values {
            #expect(AppleStyleMetadata.SceneSample.halfFloatBits(value) == Float16(value).bitPattern,
                    "value \(value)")
        }
    }

    // MARK: Helpers

    private func temporaryFolder() throws -> URL {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder
    }

    private func flatRaster(gray: UInt8, width: Int, height: Int) -> [UInt8] {
        (0..<(width * height)).flatMap { _ in [gray, gray, gray, 255] }
    }

    /// Writes a top-down BGRA raster to a JPEG with no orientation, preserving the
    /// raster's visual layout through the decode path.
    private func writeJPEG(raster: [UInt8], width: Int, height: Int) throws -> URL {
        guard let bitmap = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8,
                                     bytesPerRow: width * 4, space: CGColorSpace(name: CGColorSpace.displayP3)!,
                                     bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue
                                         | CGImageByteOrderInfo.order32Little.rawValue) else {
            throw CocoaError(.fileWriteUnknown)
        }
        raster.withUnsafeBytes { raw in
            memcpy(bitmap.data!, raw.baseAddress, min(raster.count, width * height * 4))
        }
        guard let cg = bitmap.makeImage() else { throw CocoaError(.fileWriteUnknown) }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("scene-\(UUID().uuidString).jpg")
        try CIContext().writeJPEGRepresentation(of: CIImage(cgImage: cg), to: url,
                                                colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!)
        return url
    }

    private func fp16(_ bytes: [UInt8], at: Int) -> Double {
        Double(Float16(bitPattern: UInt16(bytes[at]) | (UInt16(bytes[at + 1]) << 8)))
    }

    private func readU32(_ bytes: [UInt8], _ at: Int) -> Int {
        Int(bytes[at]) << 24 | Int(bytes[at + 1]) << 16 | Int(bytes[at + 2]) << 8 | Int(bytes[at + 3])
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
}
