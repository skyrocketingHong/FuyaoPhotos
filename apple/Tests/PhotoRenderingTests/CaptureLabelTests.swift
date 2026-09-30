import Testing
import Foundation
import CoreText
import ImageIO
@testable import PhotoRenderingCore

struct CaptureLabelTests {
    @Test func cameraNamesFollowThePhysicalLensThroughCrops() throws {
        let tele = try #require(AppleCameraNames.resolve(make: "Apple", model: "iPhone 17 Pro Max",
            lens: "iPhone 17 Pro Max back triple camera 16.891mm f/2.8", cameraType: nil))
        #expect(tele.name == "Fusion Telephoto")
        #expect(tele.zoom(at: 100) == 4 && tele.zoom(at: 200) == 8)
        let main = try #require(AppleCameraNames.resolve(make: "Apple", model: "iPhone 18 Pro Max",
            lens: "iPhone 18 Pro Max back triple camera 6.93mm f/1.48", cameraType: 1))
        #expect(main.name == "Fusion Main" && main.zoom(at: 48) == 2)
        #expect(CardImageProcessor.number(try #require(main.zoom(at: 35))) == "1.5")
        let wide = try #require(AppleCameraNames.resolve(make: "Apple", model: "iPhone 17 Pro",
            lens: "back triple camera 2.22mm f/2.2", cameraType: 0))
        #expect(wide.name == "Fusion Ultra Wide" && wide.zoom(at: 13) == 0.5)
        #expect(AppleCameraNames.resolve(make: "Apple", model: "iPhone 14 Pro", lens: "back camera f/2.8", cameraType: 1) == nil)
        #expect(AppleCameraNames.resolve(make: "Other", model: "iPhone 18 Pro", lens: "back camera f/1.48", cameraType: 1) == nil)
        #expect(AppleCameraNames.resolve(make: "Apple", model: "iPhone 18 Pro", lens: "front camera f/1.9", cameraType: 6) == nil)
        #expect(AppleCameraNames.resolve(make: "Apple", model: "iPhone 18 Pro", lens: "unknown", cameraType: nil) == nil)
    }

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
}
