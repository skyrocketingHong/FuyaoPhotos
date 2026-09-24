import Foundation
import CoreText
import CoreGraphics
#if canImport(UIKit)
import UIKit
#else
import AppKit
#endif

nonisolated enum CardTypography {
    static func monoFont(size: CGFloat) -> CTFont {
#if canImport(UIKit)
        return UIFont.monospacedSystemFont(ofSize: size, weight: .medium) as CTFont
#else
        return NSFont.monospacedSystemFont(ofSize: size, weight: .medium) as CTFont
#endif
    }

    static func text(_ text: String, size: CGFloat, accent: Bool) -> NSAttributedString {
#if canImport(UIKit)
        let system = UIFont.systemFont(ofSize: size, weight: .medium)
        let rounded = UIFont(descriptor: system.fontDescriptor.withDesign(.rounded) ?? system.fontDescriptor, size: size) as CTFont
#else
        let system = NSFont.systemFont(ofSize: size, weight: .medium)
        let rounded = (NSFont(descriptor: system.fontDescriptor.withDesign(.rounded) ?? system.fontDescriptor, size: size) ?? system) as CTFont
#endif
        let mono = monoFont(size: size)
        let capScale = CTFontGetCapHeight(mono) / max(1, CTFontGetCapHeight(rounded))
        let descriptor = CTFontDescriptorCreateCopyWithAttributes(CTFontCopyFontDescriptor(rounded), [
            kCTFontFeatureSettingsAttribute: [
                [kCTFontOpenTypeFeatureTag: "cv04", kCTFontOpenTypeFeatureValue: 1],
                [kCTFontOpenTypeFeatureTag: "cv05", kCTFontOpenTypeFeatureValue: 1],
                [kCTFontOpenTypeFeatureTag: "pnum", kCTFontOpenTypeFeatureValue: 1]
            ]
        ] as CFDictionary)
        let font = CTFontCreateWithFontDescriptor(descriptor, size * capScale, nil)
        let one = CTFontCreateWithFontDescriptor(descriptor, size * capScale * 1.03, nil)
        let color = accent ? CGColor(red: 1, green: 218.0 / 255, blue: 69.0 / 255, alpha: 1)
            : CGColor(gray: 1, alpha: 1)
        let result = NSMutableAttributedString(string: "")
        for character in text {
            let part = String(character)
            let scalar = part.unicodeScalars.count == 1 ? part.unicodeScalars.first?.value : nil
            let selected = scalar == 49 ? one : (scalar == 48 || (50...57).contains(scalar ?? 0) ? mono : font)
            result.append(NSAttributedString(string: part, attributes: [
                NSAttributedString.Key(kCTFontAttributeName as String): selected,
                NSAttributedString.Key(kCTForegroundColorAttributeName as String): color
            ]))
        }
        return result
    }
}
