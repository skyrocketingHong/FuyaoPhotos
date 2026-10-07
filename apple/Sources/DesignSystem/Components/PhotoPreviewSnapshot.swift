import CoreGraphics
import Foundation

struct PhotoPreviewSnapshot {
    let documentID: UUID
    let sourceURL: URL
    let image: CGImage
    var overlay: CardOverlayRender?
    var usesPhotoBackdrop = true

    func render(in viewport: CGSize, sdrBase: CGImage?) -> CGImage? {
        guard viewport.width > 0, viewport.height > 0,
              let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) else { return nil }
        let pixelScale = min(2, sqrt(Double(PhotoMotionTokens.snapshotPixelLimit) / (viewport.width * viewport.height)))
        let width = max(1, Int((viewport.width * pixelScale).rounded(.up)))
        let height = max(1, Int((viewport.height * pixelScale).rounded(.up)))
        guard let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8,
            bytesPerRow: 0, space: colorSpace, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        context.scaleBy(x: CGFloat(width) / viewport.width, y: CGFloat(height) / viewport.height)
        let base = usesPhotoBackdrop ? sdrBase ?? image : image
        let scale = min(viewport.width / CGFloat(base.width), viewport.height / CGFloat(base.height))
        let frame = CGRect(x: (viewport.width - CGFloat(base.width) * scale) / 2,
            y: (viewport.height - CGFloat(base.height) * scale) / 2,
            width: CGFloat(base.width) * scale, height: CGFloat(base.height) * scale)
        context.draw(base, in: frame)
        if let overlay {
            let rect = overlay.normalizedRect
            let cardFrame = CGRect(x: frame.minX + rect.minX * frame.width,
                y: frame.maxY - rect.maxY * frame.height,
                width: rect.width * frame.width, height: rect.height * frame.height)
            let radius = overlay.normalizedRadius * frame.width
            context.addPath(CGPath(roundedRect: cardFrame, cornerWidth: radius, cornerHeight: radius, transform: nil))
            context.clip()
            context.draw(overlay.image, in: cardFrame)
        }
        return context.makeImage()
    }
}
