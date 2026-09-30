import Testing
import Foundation
import CoreText
import ImageIO
@testable import PhotoRenderingCore

struct CaptureLabelTests {
    @Test func stylesAreOptionalEditableAndDoNotBorrowAnotherField() throws {
        var card = PhotoCard()
        card[.iso] = "50"
        #expect(card.rows.map(\.field) == [.iso])
        card[.photographicStyle] = "Rose Gold"
        #expect(card.rows.last?.text == "STYLE: ROSE GOLD")
        let restored = try JSONDecoder().decode(PhotoCard.self, from: JSONEncoder().encode(card))
        #expect(restored[.photographicStyle] == "Rose Gold")
        #expect(restored.style == card.style)
        card[.photographicStyle] = " "
        #expect(card.rows.map(\.field) == [.iso])
    }

    @Test func modernAndLegacyPresetsAreNeverConfused() throws {
        let neutral = try PropertyListSerialization.propertyList(from: Data(AppleStyleGolden.TAG_84), options: [], format: nil) as! [String: Any]
        #expect(PhotographicStyleReader.modernName(neutral) == "Standard")
        var unknown = neutral; unknown["4"] = 100
        #expect(PhotographicStyleReader.modernName(unknown) == nil)
        #expect(PhotographicStyleReader.modernName(["4": 1]) == nil)
        #expect(PhotographicStyleReader.name(properties: [kCGImagePropertyMakerAppleDictionary as String: ["64": ["_3": 3]]]) == "Rich Contrast")
        #expect(PhotographicStyleReader.name(properties: [kCGImagePropertyMakerAppleDictionary as String: ["84": unknown, "64": ["_3": 3]]]) == nil)
    }

    @Test func explicitStyleNamesComeFromMetadata() throws {
        let xml = """
        <x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description xmlns:camera="http://ns.xiaomi.com/photos/1.0/camera/" camera:LeicaStyle="Leica Authentic"/>
        </rdf:RDF></x:xmpmeta>
        """
        let metadata = try #require(CGImageMetadataCreateFromXMPData(Data(xml.utf8) as CFData))
        #expect(PhotographicStyleReader.name(properties: [:], metadata: metadata) == "Leica Authentic")
        #expect(PhotographicStyleReader.readableName("66048") == nil)
        #expect(PhotographicStyleReader.readableName("Style\u{0}name") == nil)
    }

    @Test func cardTypographyPinsVariableWeightAndKeepsMixedDigits() {
        let text = CardTypography.text("H102", size: 10.5, accent: false)
        var fonts: [CTFont] = []
        text.enumerateAttribute(NSAttributedString.Key(kCTFontAttributeName as String), in: NSRange(location: 0, length: text.length)) { value, _, _ in
            guard let value else { return }
            let font = value as! CTFont
            fonts.append(font)
            let axes = CTFontCopyVariationAxes(font) as? [[CFString: Any]] ?? []
            if axes.contains(where: { ($0[kCTFontVariationAxisIdentifierKey] as? NSNumber)?.intValue == 0x77676874 }) {
                let variations = CTFontCopyVariation(font) as? [NSNumber: NSNumber] ?? [:]
                #expect(variations[NSNumber(value: 0x77676874)]?.doubleValue == 500)
            }
            #expect(!(CTFontCopyPostScriptName(font) as String).contains("Times"))
        }
        #expect(fonts.count >= 3)
    }

    @Test func referenceLettersUseCompactRoundedOutlines() throws {
        let text = CardTypography.text("CGS", size: 40, accent: false)
        for index in 0..<text.length {
            let font = try #require(text.attribute(NSAttributedString.Key(kCTFontAttributeName as String),
                at: index, effectiveRange: nil)) as! CTFont
            #expect((CTFontCopyFamilyName(font) as String).contains("Compact Rounded"))
        }
    }
}
