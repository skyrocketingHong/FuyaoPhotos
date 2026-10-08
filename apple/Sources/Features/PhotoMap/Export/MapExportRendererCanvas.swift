import SwiftUI
import MapKit
import PhotoMapCore
import CoreText

@MainActor final class MapExportCanvas {
    let context: CGContext
    let scale: CGFloat
    let colorScheme: ColorScheme
    private var ink: CGColor { colorScheme == .dark ? CGColor(gray: 1, alpha: 1) : CGColor(gray: 0.08, alpha: 1) }
    private var surface: CGColor { colorScheme == .dark ? CGColor(gray: 0.12, alpha: 1) : CGColor(gray: 1, alpha: 1) }

    init(pixels: Int, colorScheme: ColorScheme) throws {
        self.colorScheme = colorScheme
        scale = CGFloat(pixels) / 512
        guard let colorSpace = CGColorSpace(name: CGColorSpace.sRGB),
              let context = CGContext(data: nil, width: pixels, height: pixels, bitsPerComponent: 8,
                bytesPerRow: 0, space: colorSpace, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        else { throw MapExportError.imageFailed }
        self.context = context
        context.scaleBy(x: scale, y: scale)
        context.translateBy(x: 0, y: 512)
        context.scaleBy(x: 1, y: -1)
        context.setFillColor(surface)
        context.fill(CGRect(x: 0, y: 0, width: 512, height: 512))
        context.interpolationQuality = .high
    }

    func draw(_ image: CGImage, in rect: CGRect) {
        context.saveGState()
        context.translateBy(x: rect.minX, y: rect.maxY)
        context.scaleBy(x: 1, y: -1)
        context.draw(image, in: CGRect(origin: .zero, size: rect.size))
        context.restoreGState()
    }

    func drawPhoto(_ image: CGImage?, cluster: MapCluster, at point: CGPoint) {
        let rect = CGRect(x: point.x - 32, y: point.y - 32, width: 64, height: 64)
        let path = CGPath(roundedRect: rect, cornerWidth: 12, cornerHeight: 12, transform: nil)
        context.saveGState()
        context.addPath(path)
        context.clip()
        context.setFillColor(surface)
        context.fill(rect)
        if let image {
            let factor = max(rect.width / CGFloat(image.width), rect.height / CGFloat(image.height))
            let size = CGSize(width: CGFloat(image.width) * factor, height: CGFloat(image.height) * factor)
            draw(image, in: CGRect(x: rect.midX - size.width / 2, y: rect.midY - size.height / 2,
                                   width: size.width, height: size.height))
        } else if let symbol = MapExportNativeArtwork.photoSymbol(colorScheme: colorScheme, scale: scale) {
            draw(symbol, in: rect.insetBy(dx: 18, dy: 18))
        }
        context.restoreGState()
        context.setStrokeColor(CGColor(gray: 1, alpha: 1))
        context.setLineWidth(2)
        context.addPath(CGPath(roundedRect: rect.insetBy(dx: 1, dy: 1),
            cornerWidth: 11, cornerHeight: 11, transform: nil))
        context.strokePath()
        if cluster.count > 1 {
            let label = cluster.count.formatted()
            let width = textWidth(label, size: 11, bold: true) + 10
            let badge = CGRect(x: rect.minX + 5, y: rect.maxY - 23, width: min(54, width), height: 18)
            fill(badge, radius: 5, color: CGColor(gray: 0, alpha: 0.85))
            text(label, at: CGPoint(x: badge.midX, y: badge.minY + 2), size: 11,
                bold: true, color: CGColor(gray: 1, alpha: 1), centered: true)
        }
    }

    func drawMarker(_ cluster: MapCluster, at point: CGPoint) throws {
        let artwork = try MapExportNativeArtwork.marker(count: cluster.count,
            colorScheme: colorScheme, scale: scale)
        draw(artwork.image, in: CGRect(x: point.x - artwork.size.width / 2,
            y: point.y - artwork.size.height, width: artwork.size.width, height: artwork.size.height))
    }

    func drawHeatmap(_ clusters: [MapCluster], projection: MapExportProjection,
                     radius: Double, opacity: Double) throws {
        let overlay = HeatmapOverlay(clusters: clusters, radius: radius, opacity: opacity)
        let renderer = HeatmapRenderer(overlay: overlay)
        let world = MKMapRect.world.width
        let center = projection.centerMapPoint
        let anchor = projection.centerOnCanvas
        let zoomScale = 1 / projection.mapPointsPerPoint
        let rendererOrigin = renderer.point(for: center)
        // Repeat only the neighboring world copies so date-line selections keep both halves.
        for shift in -1...1 {
            try Task.checkCancellation()
            let shiftedX = anchor.x + Double(shift) * world * zoomScale
            let visibleRect = MKMapRect(
                x: center.x - shiftedX / zoomScale,
                y: center.y - anchor.y / zoomScale,
                width: 512 / zoomScale, height: 512 / zoomScale)
            context.saveGState()
            context.clip(to: CGRect(x: 0, y: 0, width: 512, height: 512))
            context.translateBy(x: shiftedX, y: anchor.y)
            context.scaleBy(x: zoomScale, y: zoomScale)
            context.translateBy(x: -rendererOrigin.x, y: -rendererOrigin.y)
            renderer.draw(visibleRect, zoomScale: zoomScale, in: context)
            context.restoreGState()
        }
    }

    func drawAttribution() {
        let label = "Apple Maps"
        let width = textWidth(label, size: 10) + 12
        let rect = CGRect(x: 512 - width - 12, y: 482, width: width, height: 18)
        fill(rect, radius: 5, color: surface)
        text(label, at: CGPoint(x: rect.midX, y: rect.minY + 3), size: 10, centered: true)
    }

    func finish() throws -> CGImage {
        guard let image = context.makeImage() else { throw MapExportError.imageFailed }
        return image
    }

    private func fill(_ rect: CGRect, radius: CGFloat, color: CGColor) {
        context.setFillColor(color)
        context.addPath(CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil))
        context.fillPath()
    }

    private func line(_ value: String, size: CGFloat, bold: Bool, color: CGColor) -> CTLine {
#if os(macOS)
        let font = NSFont.systemFont(ofSize: size, weight: bold ? .semibold : .regular)
#else
        let font = UIFont.systemFont(ofSize: size, weight: bold ? .semibold : .regular)
#endif
        return CTLineCreateWithAttributedString(NSAttributedString(string: value, attributes: [
            NSAttributedString.Key(kCTFontAttributeName as String): font,
            NSAttributedString.Key(kCTForegroundColorAttributeName as String): color
        ]))
    }

    private func textWidth(_ value: String, size: CGFloat, bold: Bool = false) -> CGFloat {
        CGFloat(CTLineGetTypographicBounds(line(value, size: size, bold: bold, color: ink), nil, nil, nil))
    }

    private func text(_ value: String, at point: CGPoint, size: CGFloat,
                      bold: Bool = false, color: CGColor? = nil, centered: Bool = false) {
        let line = line(value, size: size, bold: bold, color: color ?? ink)
        var ascent: CGFloat = 0
        let width = CGFloat(CTLineGetTypographicBounds(line, &ascent, nil, nil))
        context.saveGState()
        context.translateBy(x: point.x - (centered ? width / 2 : 0), y: point.y + ascent)
        context.scaleBy(x: 1, y: -1)
        context.textMatrix = .identity
        context.textPosition = .zero
        CTLineDraw(line, context)
        context.restoreGState()
    }
}
