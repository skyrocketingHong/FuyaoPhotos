import Testing
import Foundation
@testable import PhotoRenderingCore

struct AppleTextureStylesTests {
    @Test func textureInfoCarriesTheNativeContract() throws {
        let payload = AppleTextureStyles.textureInfoPayload(grainSeed: 104)
        #expect(String(decoding: payload[0..<8], as: UTF8.self) == "bplist00")
        let text = String(decoding: payload, as: UTF8.self)
        for key in ["Preset", "Standard", "CaptureType", "LF", "CaptureMode", "Still", "PortType",
                    "PortTypeBack", "HardwareModel", "iPhone 18 Pro", "TextureStylePeopleDataVersion", "FilmGrainSeed"] {
            #expect(text.contains(key), "textureInfo lacks \(key)")
        }
        // The people-data version and the grain seed are 8-bit inline integers in the
        // observed payload; both must appear after their key strings.
        #expect(payload.count >= 150 && payload.count <= 400)
        #expect(contains(payload, [0x10, 3]), "people data version 3 missing")
        #expect(contains(payload, [0x10, 104]), "grain seed 104 missing")
    }

    @Test func grainSeedIsStableAndBounded() {
        #expect(AppleTextureStyles.grainSeedFor("IMG_1234.jpg") == 1363220817)
        #expect(AppleTextureStyles.grainSeedFor("IMG_8565.HEIC") == 1018958189)
        #expect(AppleTextureStyles.grainSeedFor("a") == 97)
        #expect(AppleTextureStyles.grainSeedFor("IMG_1.jpg") != AppleTextureStyles.grainSeedFor("IMG_2.jpg"))
        #expect((0...0x7fff_ffff).contains(AppleTextureStyles.grainSeedFor("a")))
    }

    @Test func matteURNsMatchTheNativeOrder() {
        #expect(AppleTextureStyles.semanticMatteURNS.count == 12)
        #expect(AppleTextureStyles.semanticMatteURNS.first == "tag:apple.com,2026:photo:aux:semanticnosematte")
        #expect(AppleTextureStyles.semanticMatteURNS.last == "tag:apple.com,2026:photo:aux:semanticfaceskinmatte")
        #expect(Set(AppleTextureStyles.semanticMatteURNS).count == 12)
    }

    private func contains(_ haystack: [UInt8], _ needle: [UInt8]) -> Bool {
        guard needle.count <= haystack.count else { return false }
        for start in 0...(haystack.count - needle.count) {
            if Array(haystack[start..<start + needle.count]) == needle { return true }
        }
        return false
    }
}
