import Foundation
import CoreImage
import Testing
@testable import PhotoRenderingCore

struct CardDetailPreviewTests {
    @Test(arguments: [0.65, 1.4, 2.3])
    func fixedViewportPreservesTheEntireLargeTextCard(_ aspect: Double) async throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let source = folder.appendingPathComponent("source.png")
        let image = CIImage(color: CIColor(red: 0.3, green: 0.5, blue: 0.7))
            .cropped(to: CGRect(x: 0, y: 0, width: 1600, height: 1200))
        let colorSpace = try #require(CGColorSpace(name: CGColorSpace.sRGB))
        try CIContext().writePNGRepresentation(of: image, to: source, format: .RGBA8, colorSpace: colorSpace)
        var card = PhotoCard()
        card[.device] = "Camera"
        card[.author] = "A photographer with a long credit that wraps across several lines"
        card[.location] = "A location with enough detail to exercise the full card height"
        card[.iso] = "400"
        card.style.textScale = 1.8
        let detail = try await CardImageProcessor.shared.previewCardDetail(source, card: card, aspectRatio: aspect)
        let width = CGFloat(detail.image.width), height = CGFloat(detail.image.height)
        #expect(abs(width / height - aspect) < 2 / height)
        #expect(max(width, height) <= 1601)
        let bounds = CGRect(x: -1, y: -1, width: width + 2, height: height + 2)
        #expect(bounds.contains(detail.cardRect))
        let authorRects = try #require(detail.textRects[.author])
        #expect(authorRects.count > 1)
        #expect(authorRects.allSatisfy { bounds.contains($0) })
    }
}
