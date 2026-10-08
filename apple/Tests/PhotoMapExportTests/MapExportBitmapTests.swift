import Testing
import Foundation
import CoreGraphics
@testable import PhotoMapExportBitmapCore

struct MapExportBitmapTests {
    private let colors: [[UInt8]] = [[255, 0, 0, 255], [0, 255, 0, 255],
                                     [0, 0, 255, 255], [255, 255, 0, 255]]

    @Test func singleTileKeepsNorthAtTheTop() throws {
        let source = try quadrants()
        let result = try MapExportBitmap.stitch([(CGRect(x: 0, y: 0, width: 8, height: 8), source)], pixels: 8)
        try checkQuadrants(result, originX: 0, originY: 0, side: 8)
    }

    @Test func fourTilesKeepTheirRowColumnAndInternalOrientation() throws {
        let pieces = try (0..<4).map { index in
            (CGRect(x: (index % 2) * 8, y: (index / 2) * 8, width: 8, height: 8),
             try quadrants(intensity: UInt8(255 - index * 40)))
        }
        let result = try MapExportBitmap.stitch(pieces, pixels: 16)
        for index in 0..<4 {
            try checkQuadrants(result, originX: (index % 2) * 8, originY: (index / 2) * 8,
                               side: 8, intensity: UInt8(255 - index * 40))
        }
    }

    @Test func differentBackingScaleDoesNotInvertOrMoveTheTile() throws {
        let source = try quadrants()
        let result = try MapExportBitmap.stitch([(CGRect(x: 4, y: 4, width: 16, height: 16), source)], pixels: 24)
        try checkQuadrants(result, originX: 4, originY: 4, side: 16)
        #expect(try pixel(result, x: 0, y: 0) == [0, 0, 0, 0])
    }

    @Test func invalidTileGeometryIsRejected() throws {
        let source = try quadrants()
        #expect(throws: MapExportBitmap.Failure.self) {
            try MapExportBitmap.stitch([(CGRect(x: 0, y: 0, width: 12, height: 12), source)], pixels: 8)
        }
    }

    private func quadrants(intensity: UInt8 = 255) throws -> CGImage {
        var bytes: [UInt8] = []
        for y in 0..<8 {
            for x in 0..<8 {
                bytes.append(contentsOf: color((y < 4 ? 0 : 2) + (x < 4 ? 0 : 1), intensity: intensity))
            }
        }
        let provider = try #require(CGDataProvider(data: Data(bytes) as CFData))
        return try #require(CGImage(width: 8, height: 8, bitsPerComponent: 8, bitsPerPixel: 32,
            bytesPerRow: 32, space: CGColorSpace(name: CGColorSpace.sRGB)!,
            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
            provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent))
    }

    private func checkQuadrants(_ image: CGImage, originX: Int, originY: Int,
                                side: Int, intensity: UInt8 = 255) throws {
        for index in 0..<4 {
            let x = originX + (index % 2 == 0 ? side / 4 : side * 3 / 4)
            let y = originY + (index < 2 ? side / 4 : side * 3 / 4)
            #expect(try pixel(image, x: x, y: y) == color(index, intensity: intensity))
        }
    }

    private func color(_ index: Int, intensity: UInt8) -> [UInt8] {
        colors[index].enumerated().map { component, value in
            component < 3 && value != 0 ? intensity : value
        }
    }

    private func pixel(_ image: CGImage, x: Int, y: Int) throws -> [UInt8] {
        let data = try #require(image.dataProvider?.data)
        let pointer = try #require(CFDataGetBytePtr(data))
        let offset = y * image.bytesPerRow + x * 4
        return Array(UnsafeBufferPointer(start: pointer + offset, count: 4))
    }
}
