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
        referenceFont(size: size, monospaced: true)
    }

    private static func referenceFont(size: CGFloat, monospaced: Bool) -> CTFont {
#if canImport(UIKit)
        var result: CTFont!
        // Card text is image content; Bold Text belongs to the surrounding interface.
        UITraitCollection(legibilityWeight: .regular).performAsCurrent {
            let system = monospaced ? UIFont.monospacedSystemFont(ofSize: size, weight: .medium)
                : UIFont.systemFont(ofSize: size, weight: .medium)
            let descriptor = monospaced ? system.fontDescriptor
                : system.fontDescriptor.withDesign(.rounded) ?? system.fontDescriptor
            result = fixedWeight(UIFont(descriptor: descriptor, size: size) as CTFont, size: size)
        }
        return result
#else
        let system = monospaced ? NSFont.monospacedSystemFont(ofSize: size, weight: .medium)
            : NSFont.systemFont(ofSize: size, weight: .medium)
        let descriptor = monospaced ? system.fontDescriptor
            : system.fontDescriptor.withDesign(.rounded) ?? system.fontDescriptor
        return fixedWeight((NSFont(descriptor: descriptor, size: size) ?? system) as CTFont, size: size)
#endif
    }

    private static func fixedWeight(_ font: CTFont, size: CGFloat) -> CTFont {
        let weightAxis = NSNumber(value: 0x77676874) // OpenType wght
        let axes = CTFontCopyVariationAxes(font) as? [[CFString: Any]] ?? []
        guard axes.contains(where: { ($0[kCTFontVariationAxisIdentifierKey] as? NSNumber) == weightAxis }) else {
            return font
        }
        var variations = CTFontCopyVariation(font) as? [NSNumber: NSNumber] ?? [:]
        variations[weightAxis] = 500
        let descriptor = CTFontDescriptorCreateCopyWithAttributes(CTFontCopyFontDescriptor(font), [
            kCTFontVariationAttribute: variations
        ] as CFDictionary)
        return CTFontCreateWithFontDescriptor(descriptor, size, nil)
    }

    static func text(_ text: String, size: CGFloat, accent: Bool) -> NSAttributedString {
        let rounded = referenceFont(size: size, monospaced: false)
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
