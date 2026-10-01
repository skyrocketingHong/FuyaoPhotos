import Foundation
import Testing
@testable import PhotoRenderingCore

struct PhotoPreviewMetricsTests {
    @Test func wideSpaceGoesToThePhotoAndKeepsTheInspectorBounded() {
        let medium = PhotoPreviewMetrics(available: CGSize(width: 1000, height: 800))
        let large = PhotoPreviewMetrics(available: CGSize(width: 1500, height: 1000))
        #expect(medium.isWide && large.isWide)
        #expect(medium.width == 660 && medium.inspectorWidth == 340)
        #expect(large.width == 1100 && large.inspectorWidth == 400)
        #expect(medium.imageHeight > 600 && large.imageHeight > medium.imageHeight)
        #expect(medium.width + medium.inspectorWidth == 1000)
        #expect(large.width + large.inspectorWidth == 1500)
    }

    @Test func compactAndShortWindowsKeepNonnegativeGeometry() {
        let compact = PhotoPreviewMetrics(available: CGSize(width: 390, height: 760))
        #expect(!compact.isWide && compact.width == 390)
        #expect(compact.imageHeight == (390 - 40) * 0.75)
        for size in [CGSize(width: 320, height: 180), CGSize(width: 800, height: 100), .zero] {
            let value = PhotoPreviewMetrics(available: size)
            #expect(value.width >= 0 && value.imageHeight >= 0 && value.height >= 0)
        }
    }
}
