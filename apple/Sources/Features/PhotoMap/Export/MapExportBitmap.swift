import CoreGraphics

nonisolated enum MapExportBitmap {
    enum Failure: Error { case invalidGeometry, allocationFailed }

    static func stitch(_ pieces: [(rect: CGRect, cgImage: CGImage)], pixels: Int) throws -> CGImage {
        guard pixels > 0, pixels <= 8192, !pieces.isEmpty,
              pieces.allSatisfy({ piece in
                  let r = piece.rect
                  return r.minX.isFinite && r.minY.isFinite && r.width.isFinite && r.height.isFinite
                      && r.minX >= 0 && r.minY >= 0 && r.width > 0 && r.height > 0
                      && r.maxX <= CGFloat(pixels) && r.maxY <= CGFloat(pixels)
              }) else { throw Failure.invalidGeometry }
        guard let colorSpace = CGColorSpace(name: CGColorSpace.sRGB),
              let context = CGContext(data: nil, width: pixels, height: pixels,
                  bitsPerComponent: 8, bytesPerRow: 0, space: colorSpace,
                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { throw Failure.allocationFailed }
        context.clear(CGRect(x: 0, y: 0, width: pixels, height: pixels))
        context.interpolationQuality = .high
        context.translateBy(x: 0, y: CGFloat(pixels))
        context.scaleBy(x: 1, y: -1)
        for piece in pieces {
            // The layout is top-left; each CGImage still needs its own upright drawing transform.
            context.saveGState()
            context.translateBy(x: piece.rect.minX, y: piece.rect.maxY)
            context.scaleBy(x: 1, y: -1)
            context.draw(piece.cgImage, in: CGRect(origin: .zero, size: piece.rect.size))
            context.restoreGState()
        }
        guard let image = context.makeImage() else { throw Failure.allocationFailed }
        return image
    }
}
