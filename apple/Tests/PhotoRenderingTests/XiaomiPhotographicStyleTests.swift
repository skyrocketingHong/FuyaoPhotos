import CryptoKit
import Foundation
import ImageIO
import Testing
@testable import PhotoRenderingCore

struct XiaomiPhotographicStyleTests {
    private static var sample: URL {
        if let path = ProcessInfo.processInfo.environment["FUYAO_LEICA_SAMPLE"] { return URL(fileURLWithPath: path) }
        return LensProfileFileTests.root.appendingPathComponent("docs/samples/leica-style-20261001/MVIMG_20260928_200322.jpg")
    }

    private func packet(_ auxiliary: Any) throws -> Data {
        let data = try JSONSerialization.data(withJSONObject: ["version": "32", "889e": auxiliary])
        return XiaomiPhotographicStyleReader.prefix + Data([1, 1]) + data
    }

    @Test func explicitNameIsReadFromTheSharedPrivatePacket() throws {
        let json = try Data(contentsOf: LensProfileFileTests.root.appendingPathComponent("shared/fixtures/xiaomi-style-standard.json"))
        let payload = XiaomiPhotographicStyleReader.prefix + Data([1, 1]) + json
        #expect(XiaomiPhotographicStyleReader.name(in: [payload]) == "Standard")
        let named = try packet(#"{"version":4,"filterName":"Leica Natural"}"#)
        #expect(XiaomiPhotographicStyleReader.name(in: [named]) == "Leica Natural")
    }

    @Test func unknownNumbersMalformedAndAmbiguousPacketsStayEmpty() throws {
        for value in [#"{"filterId":66048}"#, #"{"filterName":66048}"#, #"{"filterName":"66048"}"#,
                      #"{"filterName":"Style\nname"}"#, "invalid", #"["Standard"]"#] {
            #expect(XiaomiPhotographicStyleReader.name(in: [try packet(value)]) == nil)
        }
        #expect(XiaomiPhotographicStyleReader.name(in: [try packet(["filterName": "Standard"])]) == nil)
        let complete = try packet(#"{"filterName":"Standard"}"#)
        #expect(XiaomiPhotographicStyleReader.name(in: [complete, complete]) == nil)
        #expect(XiaomiPhotographicStyleReader.name(in: [Data(complete.dropLast())]) == nil)
        var multipart = complete; multipart[XiaomiPhotographicStyleReader.prefix.count + 1] = 2
        #expect(XiaomiPhotographicStyleReader.name(in: [multipart]) == nil)
        let large = XiaomiPhotographicStyleReader.prefix + Data([1, 1]) + Data(repeating: 32, count: 65_534)
        #expect(XiaomiPhotographicStyleReader.name(in: [large]) == nil)
        let invalidUTF8 = XiaomiPhotographicStyleReader.prefix + Data([1, 1, 0xff])
        #expect(XiaomiPhotographicStyleReader.name(in: [invalidUTF8]) == nil)
    }

    @Test func explicitXMPNameTakesPriorityOverVendorFilterName() throws {
        let xml = #"<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description xmlns:c="http://ns.xiaomi.com/photos/1.0/camera/" c:LeicaStyle="Leica Authentic"/></rdf:RDF></x:xmpmeta>"#
        let metadata = try #require(CGImageMetadataCreateFromXMPData(Data(xml.utf8) as CFData))
        #expect(PhotographicStyleReader.name(properties: [:], metadata: metadata, vendorName: "Standard") == "Leica Authentic")
        #expect(PhotographicStyleReader.name(properties: [:], vendorName: "66048") == nil)
    }

    @Test func prefixesComeFromConfigurationAndNeverReplaceMissingNames() {
        #expect(PhotographicStyleReader.displayName("Standard", prefix: "Leica") == "Leica Standard")
        #expect(PhotographicStyleReader.displayName("Standard", prefix: "Other Brand") == "Other Brand Standard")
        #expect(PhotographicStyleReader.displayName("Standard", prefix: nil) == "Standard")
        #expect(PhotographicStyleReader.displayName("Leica Standard", prefix: "Leica") == "Leica Standard")
        #expect(PhotographicStyleReader.displayName("leica Natural", prefix: "Leica") == "leica Natural")
        #expect(PhotographicStyleReader.displayName(nil, prefix: "Leica").isEmpty)
        #expect(PhotographicStyleReader.displayName("", prefix: "Leica").isEmpty)
    }

    @Test(.enabled(if: FileManager.default.fileExists(atPath: sample.path)))
    func originalPhotoPopulatesCardAndReportWithoutChangingTheFile() async throws {
        let before = SHA256.hash(data: try Data(contentsOf: Self.sample))
        let scan = try PhotoMediaInspector.scanJPEG(Self.sample)
        #expect(scan.vendorPhotographicStyle == "Standard")
        let capture = try await CardImageProcessor.shared.read(Self.sample, author: "")
        #expect(capture.card[.photographicStyle] == "Standard")
        #expect(capture.card.rows.last?.text == "STYLE: STANDARD")
        let profile = LensProfile(device: capture.card[.device], exifModel: capture.card[.device], name: "Telephoto",
            facing: .back, equivalentMin: 100, equivalentMax: 100, physicalMin: 26.5, physicalMax: 26.5, stylePrefix: "Leica")
        let imported = try LensProfileFile.decode(LensProfileFile(profiles: [profile]).encoded()).profiles()
        let configured = try await CardImageProcessor.shared.read(Self.sample, author: "", profiles: imported)
        #expect(configured.card[.photographicStyle] == "Leica Standard")
        #expect(configured.card.rows.last?.text == "STYLE: LEICA STANDARD")
        let report = MediaMetadataReportReader.read(url: Self.sample, isLivePhoto: false)
        #expect(report.sections.flatMap(\.rows).contains { $0.labelKey == "card.field.photographicStyle" && $0.value == "Standard" })
        #expect(SHA256.hash(data: try Data(contentsOf: Self.sample)) == before)
    }
}
