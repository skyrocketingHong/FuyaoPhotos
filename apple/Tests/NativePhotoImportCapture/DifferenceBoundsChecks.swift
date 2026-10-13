import CoreGraphics
import Foundation

@MainActor enum PhotoImportDifferenceChecks {
    private enum Failure: Error { case mismatch(Int), image }

    static func run() throws -> Int {
        var randomState: UInt64 = 0x991812
        func random(_ limit: Int) -> Int {
            randomState = randomState &* 6_364_136_223_846_793_005 &+ 1
            return Int(randomState >> 32) % limit
        }
        let cases = 125
        for trial in 0..<cases {
            let width = 64, height = 48, stride = width * 4 + (trial % 3) * 16
            var background = [UInt8](repeating: 100, count: stride * height)
            for y in 0..<height {
                for x in 0..<width { background[y * stride + x * 4 + 3] = 255 }
            }
            var foreground = background
            func mark(_ x: Int, _ y: Int, _ channel: Int, _ delta: Int) {
                let index = y * stride + x * 4 + channel
                foreground[index] = UInt8(Int(background[index]) + (channel == 3 ? -delta : delta))
            }
            let content = CGRect(x: 17.3, y: 13.7, width: 21.4, height: 17.2)
            if trial < 4 {
                // Each channel, including alpha, gets an enclosing border, diagonal-only connection,
                // one-pixel shadow and a disconnected scroller. A difference of five must stay out.
                for x in 13...43 { mark(x, 9, trial, 6); mark(x, 35, trial, 6) }
                for y in 9...35 { mark(13, y, trial, 6); mark(43, y, trial, 6) }
                for n in 0..<10 { mark(14 + n, 10 + n, trial, 6) }
                for y in 0..<height { mark(width - 2, y, trial, 60) }
                mark(12, 8, trial, 6)
                mark(11, 7, trial, 5)
            } else if trial == 4 {
                // The native group border can enclose content without sharing any changed content pixel.
                for x in 13...43 { mark(x, 9, 0, 6); mark(x, 35, 0, 6) }
                for y in 9...35 { mark(13, y, 0, 6); mark(43, y, 0, 6) }
                for y in 0..<height { mark(width - 2, y, 0, 60) }
            } else if trial < 123 {
                for _ in 0..<400 {
                    mark(random(width), random(height), random(4), [5, 6, 7, 60][random(4)])
                }
            } else if trial == 123 {
                mark(20, 20, 0, 5)
            }
            let first = try image(foreground, width: width, height: height, stride: stride)
            let second = try image(background, width: width, height: height, stride: stride)
            let expected = reference(foreground, background, width: width, height: height,
                stride: stride, content: content)
            let actual: CGRect?
            do {
                actual = try PhotoImportNativeSnapshotter.differenceBounds(first, second,
                    content: content, deadline: .now.advanced(by: .seconds(2)))
            } catch PhotoImportNativeSnapshotter.Failure.invalidBounds {
                actual = nil
            }
            guard expected == actual else { throw Failure.mismatch(trial) }
        }
        return cases
    }

    private static func image(_ bytes: [UInt8], width: Int, height: Int, stride: Int) throws -> CGImage {
        guard let provider = CGDataProvider(data: Data(bytes) as CFData),
              let space = CGColorSpace(name: CGColorSpace.sRGB),
              let image = CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32,
                bytesPerRow: stride, space: space,
                bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)
        else { throw Failure.image }
        return image
    }

    private static func reference(_ first: [UInt8], _ second: [UInt8], width: Int, height: Int,
                                  stride: Int, content: CGRect) -> CGRect? {
        var changed = [Bool](repeating: false, count: width * height)
        for y in 0..<height {
            for x in 0..<width {
                let offset = y * stride + x * 4
                changed[y * width + x] = (0..<4).contains { abs(Int(first[offset + $0]) - Int(second[offset + $0])) >= 6 }
            }
        }
        var result = CGRect.null
        for seed in changed.indices where changed[seed] {
            var queue = [seed], head = 0
            changed[seed] = false
            var minX = width, minY = height, maxX = -1, maxY = -1
            var touchesContent = false
            while head < queue.count {
                let pixel = queue[head], x = pixel % width, y = pixel / width
                head += 1
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
                touchesContent = touchesContent || content.contains(CGPoint(x: x, y: y))
                for dy in -1...1 {
                    for dx in -1...1 {
                        let nextX = x + dx, nextY = y + dy
                        guard nextX >= 0, nextX < width, nextY >= 0, nextY < height else { continue }
                        let next = nextY * width + nextX
                        if changed[next] { changed[next] = false; queue.append(next) }
                    }
                }
            }
            let component = CGRect(x: minX, y: minY, width: maxX - minX + 1, height: maxY - minY + 1)
            if touchesContent || component.contains(content) { result = result.union(component) }
        }
        return result.isNull || result.isEmpty ? nil : result
    }
}
