import Foundation
import CoreImage
import CoreText
import ImageIO

nonisolated struct CardLayout {
    struct Line {
        let field: CardField
        let text: NSAttributedString
        let baseline: CGPoint
        let bounds: CGRect
    }
    let rect: CGRect
    let lines: [Line]
    let radius: CGFloat
    let blur: CGFloat

    init(size: CGSize, card: PhotoCard) throws {
        let style = card.style
        let base = min(size.width, size.height) / 859
        let unit = base * style.scale
        let fontSize = 10.5 * style.textScale
        let lineHeight = 12.5 * style.textScale
        let width = 177.0
        var wrapped: [(field: CardField, text: NSAttributedString, accent: Bool)] = []
        for row in card.rows {
            let string = CardTypography.text(row.text, size: fontSize, accent: row.accent)
            let setter = CTTypesetterCreateWithAttributedString(string)
            var start = 0
            while start < string.length {
                let length = CTTypesetterSuggestLineBreak(setter, start, width)
                guard length > 0 else { throw CardError.overflow }
                wrapped.append((row.field, string.attributedSubstring(from: NSRange(location: start, length: length)), row.accent))
                start += length
            }
        }
        let both = wrapped.contains { $0.accent } && wrapped.contains { !$0.accent }
        let contentHeight = Double(wrapped.count) * lineHeight + (both ? 7 : 0)
        let height = max(168, contentHeight + 36) * unit
        let cardWidth = 215 * unit
        guard height <= size.height, cardWidth <= size.width else { throw CardError.overflow }
        rect = CGRect(x: size.width - min(style.rightInset * base, size.width - cardWidth) - cardWidth,
                      y: min(style.bottomInset * base, size.height - height), width: cardWidth, height: height)
        radius = min(style.cornerRadius * unit, min(cardWidth, height) / 2)
        blur = style.blur * unit
        var top = height - (height - contentHeight * unit) / 2
        var previousAccent = wrapped.first?.accent ?? false
        lines = wrapped.map { field, string, accent in
            if previousAccent && !accent { top -= 7 * unit }
            let font = CardTypography.monoFont(size: fontSize)
            let baseline = CGPoint(x: 19 * unit, y: top - CTFontGetCapHeight(font) * unit)
            let ctLine = CTLineCreateWithAttributedString(string)
            var ascent: CGFloat = 0
            var descent: CGFloat = 0
            let advance = CTLineGetTypographicBounds(ctLine, &ascent, &descent, nil)
            let bounds = CGRect(x: baseline.x, y: baseline.y - descent * unit,
                                width: advance * unit, height: (ascent + descent) * unit)
            let result = Line(field: field, text: string, baseline: baseline, bounds: bounds)
            top -= lineHeight * unit
            previousAccent = accent
            return result
        }
    }

    func normalizedTextRects(for field: CardField) -> [CGRect] {
        lines.filter { $0.field == field }.compactMap { line in
            let bounds = line.bounds.intersection(CGRect(origin: .zero, size: rect.size))
            guard !bounds.isNull, bounds.width > 0, bounds.height > 0 else { return nil }
            return CGRect(x: bounds.minX / rect.width,
                          y: (rect.height - bounds.maxY) / rect.height,
                          width: bounds.width / rect.width,
                          height: bounds.height / rect.height)
        }
    }
}

nonisolated enum CardRenderer {
    static func render(_ image: CIImage, card: PhotoCard) throws -> CIImage {
        guard !card.rows.isEmpty else { return image }
        let layout = try CardLayout(size: image.extent.size, card: card)
        let rect = layout.rect
        let width = max(1, Int(ceil(rect.width)))
        let height = max(1, Int(ceil(rect.height)))
        let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!
        guard let maskContext = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8,
                bytesPerRow: 0, space: colorSpace, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue),
              let textContext = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8,
                bytesPerRow: 0, space: colorSpace, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
            throw CardError.exportFailed
        }
        maskContext.setFillColor(CGColor(gray: 1, alpha: 1))
        maskContext.addPath(CGPath(roundedRect: CGRect(origin: .zero, size: rect.size), cornerWidth: layout.radius,
                                    cornerHeight: layout.radius, transform: nil))
        maskContext.fillPath()
        let unit = min(image.extent.width, image.extent.height) / 859 * card.style.scale
        textContext.setShouldAntialias(true)
        textContext.setAllowsFontSmoothing(false)
        for line in layout.lines {
            textContext.saveGState()
            textContext.translateBy(x: line.baseline.x, y: line.baseline.y)
            textContext.scaleBy(x: unit, y: unit)
            textContext.textPosition = .zero
            CTLineDraw(CTLineCreateWithAttributedString(line.text), textContext)
            textContext.restoreGState()
        }
        guard let maskCG = maskContext.makeImage(), let textCG = textContext.makeImage() else { throw CardError.exportFailed }
        let translation = CGAffineTransform(translationX: rect.minX, y: rect.minY)
        let mask = CIImage(cgImage: maskCG).transformed(by: translation)
        let text = CIImage(cgImage: textCG).transformed(by: translation)
        let background = image.clampedToExtent().applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: layout.blur]).cropped(to: rect)
        let tint = CIImage(color: CIColor(red: 90.0/255, green: 90.0/255, blue: 90.0/255, alpha: card.style.opacity)).cropped(to: rect)
        let panel = tint.composited(over: background)
        return text.composited(over: panel.applyingFilter("CIBlendWithMask", parameters: [
            kCIInputBackgroundImageKey: image, kCIInputMaskImageKey: mask
        ])).cropped(to: image.extent).settingContentHeadroom(image.contentHeadroom)
    }
}
