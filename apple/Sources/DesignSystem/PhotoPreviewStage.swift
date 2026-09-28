import SwiftUI

struct PhotoPreviewMetrics {
    static let columnWidth: CGFloat = 480
    static let toolHeight: CGFloat = 64
    static let inlineToolWidth: CGFloat = 56
    static let wideThreshold: CGFloat = 840

    let width: CGFloat
    let imageHeight: CGFloat
    var accessoryHeight: CGFloat { Self.toolHeight }
    let isWide: Bool
    var height: CGFloat { imageHeight + 8 + accessoryHeight }

    init(available: CGSize) {
        isWide = available.width >= Self.wideThreshold
        width = min(available.width, Self.columnWidth)
        let contentWidth = max(0, width - 2 * PhotoPageLayout.margin)
        let remainingHeight = available.height - Self.toolHeight - 8 - (isWide ? 0 : 240)
        imageHeight = min(contentWidth * 0.75, max(0, remainingHeight))
    }
}

struct PhotoPreviewStage<Media: View, Accessories: View>: View {
    let metrics: PhotoPreviewMetrics
    let imageAspectRatio: CGFloat
    @ViewBuilder let media: () -> Media
    @ViewBuilder let accessories: () -> Accessories
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(spacing: 8) {
            media()
                .frame(maxWidth: .infinity)
                .frame(height: metrics.imageHeight)
                .clipped()
                .background { PhotoImageShadow(aspectRatio: imageAspectRatio) }
            accessories()
                .frame(maxWidth: .infinity)
                .frame(height: metrics.accessoryHeight, alignment: .bottom)
        }
        .padding(.horizontal, PhotoPageLayout.margin)
        .frame(width: metrics.width, height: metrics.height)
        .tint(PhotoPreviewTheme.accent(in: colorScheme))
    }
}

struct PhotoPreviewActionRow<Content: View>: View {
    var fillsWidth = true
    @ViewBuilder let content: () -> Content

    var body: some View {
        HStack(spacing: 0) {
            ForEach(subviews: content()) { subview in
                subview.frame(maxWidth: fillsWidth ? .infinity : nil)
                    .frame(width: fillsWidth ? nil : PhotoPreviewMetrics.inlineToolWidth,
                           height: PhotoPreviewMetrics.toolHeight)
            }
        }
        .frame(height: PhotoPreviewMetrics.toolHeight)
    }
}

struct PhotoWorkspaceBackdrop: View {
    let sourceURL: URL?

    var body: some View {
        ZStack {
            PhotoPreviewTheme.surface
            if let sourceURL { PhotoAmbientBackdrop(sourceURL: sourceURL, featherEdges: false) }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
