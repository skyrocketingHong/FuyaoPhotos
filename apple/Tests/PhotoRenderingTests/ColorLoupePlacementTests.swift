import Testing
import Foundation
@testable import PhotoColorsCore

struct ColorLoupePlacementTests {
    @Test func appearsAboveAndFlipsBelowAtTop() {
        let normal = ColorLoupePlacement(finger: CGPoint(x: 195, y: 210), panel: CGSize(width: 390, height: 300))
        #expect(normal.diameter == 100)
        #expect(normal.center == CGPoint(x: 195, y: 132))
        #expect(!normal.belowFinger)
        let top = ColorLoupePlacement(finger: CGPoint(x: 195, y: 10), panel: CGSize(width: 390, height: 300))
        #expect(top.belowFinger)
        #expect(top.center == CGPoint(x: 195, y: 88))
    }

    @Test(arguments: [CGSize(width: 390, height: 300), CGSize(width: 768, height: 500), CGSize(width: 80, height: 60)])
    func lensStaysInsidePanelAtEveryEdge(_ panel: CGSize) {
        for x in [CGFloat(-20), 0, panel.width / 2, panel.width, panel.width + 20] {
            for y in [CGFloat(-20), 0, panel.height / 2, panel.height, panel.height + 20] {
                let lens = ColorLoupePlacement(finger: CGPoint(x: x, y: y), panel: panel)
                let radius = lens.diameter / 2
                #expect(lens.center.x - radius >= 0)
                #expect(lens.center.y - radius >= 0)
                #expect(lens.center.x + radius <= panel.width)
                #expect(lens.center.y + radius <= panel.height)
            }
        }
    }
}
