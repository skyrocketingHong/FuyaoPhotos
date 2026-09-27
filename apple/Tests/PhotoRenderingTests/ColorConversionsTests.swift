import Testing
import Foundation
@testable import PhotoColorsCore

struct ColorConversionsTests {
    @Test func neutralWhiteAndBlack() {
        let white = ColorConversions.sample(linearP3: SIMD3(repeating: 1), x: 0, y: 0)
        #expect(white.srgb.hex == "#FFFFFF")
        #expect(abs(white.lab.x - 100) < 0.01)
        #expect(abs(white.lab.y) < 0.02 && abs(white.lab.z) < 0.02)
        #expect(abs(white.okLab.x - 1) < 0.0001)
        let black = ColorConversions.sample(linearP3: .zero, x: 0, y: 0)
        #expect(black.srgb.hex == "#000000")
        #expect(black.cmyk == SIMD4(0, 0, 0, 1))
        #expect(black.readouts(.okLab).last?.value.contains("none") == true)
    }
    @Test func wideGamutAndHDRStayUnclippedInCSS() {
        let red = ColorConversions.sample(linearP3: SIMD3(1, 0, 0), x: 1, y: 2)
        #expect(red.p3.hex == "#FF0000")
        #expect(red.srgb.outOfGamut)
        #expect(red.srgb.values.y < 0)
        let bright = ColorConversions.sample(linearP3: SIMD3(repeating: 4), x: 0, y: 0)
        #expect(bright.srgb.values.x > 1)
        #expect(bright.xyzD65.y > 3.9)
    }
    @Test func catalogsAreCompleteAndExactMatchesWin() {
        #expect(ColorCatalog.css.flatMap { $0.name.components(separatedBy: " / ") }.count == 148)
        #expect(ColorCatalog.ral.count == 213)
        let black = ColorConversions.sample(linearP3: .zero, x: 0, y: 0)
        #expect(black.cssReference?.name == "black")
        #expect(black.ralReference != nil)
    }
}
