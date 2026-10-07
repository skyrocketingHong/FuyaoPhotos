import Foundation

nonisolated struct PhotoPreviewMetrics: Sendable {
    static let pageMargin: CGFloat = 20
    static let columnWidth: CGFloat = 480
    static let toolHeight: CGFloat = 64
    static let inlineToolWidth: CGFloat = 68
    static let wideThreshold: CGFloat = 760

    let width: CGFloat
    let imageHeight: CGFloat
    var accessoryHeight: CGFloat { Self.toolHeight }
    let isWide: Bool
    let inspectorWidth: CGFloat
    var height: CGFloat { imageHeight + 8 + accessoryHeight }

    init(available: CGSize) {
        isWide = available.width >= Self.wideThreshold
        inspectorWidth = isWide ? min(400, max(340, available.width * 0.32)) : available.width
        width = isWide ? max(0, available.width - inspectorWidth) : min(available.width, Self.columnWidth)
        let contentWidth = max(0, width - 2 * Self.pageMargin)
        let remainingHeight = available.height - Self.toolHeight - 8 - (isWide ? 40 : 240)
        let compactMinimum = min(120, max(0, available.height * 0.3))
        imageHeight = isWide ? max(0, remainingHeight) : min(contentWidth * 0.75, max(compactMinimum, remainingHeight))
    }
}
