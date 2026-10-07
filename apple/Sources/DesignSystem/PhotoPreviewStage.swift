import SwiftUI

/// The preview stage: photo column plus the accessory row pinned under it.
struct PhotoPreviewStage<Media: View, Accessories: View>: View {
    let metrics: PhotoPreviewMetrics
    let imageAspectRatio: CGFloat
    @ViewBuilder let media: () -> Media
    @ViewBuilder let accessories: () -> Accessories

    var body: some View {
        VStack(spacing: 8) {
            mediaColumn
            accessoryBar
        }
        .frame(width: metrics.width, height: metrics.height)
    }

    /// The photo column, sized and shaded by the shared preview metrics.
    var mediaColumn: some View {
        media()
            .frame(maxWidth: .infinity)
            .frame(height: metrics.imageHeight)
            .background {
                GeometryReader { geometry in
                    Color.clear.preference(key: PhotoViewportFrameKey.self, value: geometry.frame(in: .global))
                }
            }
            .clipped()
            .background { PhotoImageShadow(aspectRatio: imageAspectRatio) }
            .padding(.horizontal, PhotoPageLayout.margin)
            .frame(width: metrics.width)
            .tint(PhotoPreviewTheme.accent)
    }

    /// The accessory row pinned under the photo.
    var accessoryBar: some View {
        accessories()
            .frame(maxWidth: .infinity)
            .frame(height: metrics.accessoryHeight, alignment: .bottom)
            .padding(.horizontal, PhotoPageLayout.margin)
            .frame(width: metrics.width)
            .tint(PhotoPreviewTheme.accent)
    }
}

/// How a photo workspace page places its preview stage: this page pins the photo and accessory
/// row at the call position (`InlinePhotoPreviewPage` is the scrolling counterpart). The page
/// background comes from the preview component itself.
struct PhotoPreviewPage<Media: View, Accessories: View, Content: View>: View {
    let sourceURL: URL?
    let metrics: PhotoPreviewMetrics
    let imageAspectRatio: CGFloat
    @ViewBuilder let media: () -> Media
    @ViewBuilder let accessories: () -> Accessories
    /// Page content below (narrow) or beside (wide) the stage.
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack {
            PhotoWorkspaceBackdrop(sourceURL: sourceURL)
            foreground
        }
    }

    @ViewBuilder private var foreground: some View {
        let stage = PhotoPreviewStage(metrics: metrics, imageAspectRatio: imageAspectRatio,
                                      media: media, accessories: accessories)
        if metrics.isWide {
            HStack(alignment: .top, spacing: 0) {
                stage.padding(.vertical, PhotoPageLayout.margin)
                content()
                    .frame(width: metrics.inspectorWidth)
                    .frame(maxHeight: .infinity)
                    .background(.background)
            }
        } else {
            VStack(spacing: 0) {
                stage.mediaColumn
                    .padding(.bottom, 8)
                content()
                    .safeAreaBar(edge: .top, spacing: 0) { stage.accessoryBar }
            }
        }
    }
}

/// `.inline` photo page: the preview stage is the first scrolling element of the form and the
/// whole page, background included, travels together.
struct InlinePhotoPreviewPage<Media: View, Accessories: View, Details: View>: View {
    let sourceURL: URL?
    let metrics: PhotoPreviewMetrics
    let imageAspectRatio: CGFloat
    @ViewBuilder let media: () -> Media
    @ViewBuilder let accessories: () -> Accessories
    /// Form section content scrolling below the stage.
    @ViewBuilder let details: () -> Details

    var body: some View {
        if metrics.isWide {
            PhotoPreviewPage(sourceURL: sourceURL, metrics: metrics, imageAspectRatio: imageAspectRatio,
                             media: media, accessories: accessories) {
                Form { details() }.photoPageForm()
            }
        } else { ZStack {
            PhotoWorkspaceBackdrop(sourceURL: sourceURL)
            Form {
                PhotoPreviewStage(metrics: metrics, imageAspectRatio: imageAspectRatio,
                    media: media, accessories: accessories)
                    .listRowInsets(EdgeInsets())
                    .padding(.horizontal, -PhotoPageLayout.margin)
                details()
            }
            .photoPageForm()
            .scrollContentBackground(.hidden)
        } }
    }
}

struct PhotoPreviewActionRow<Content: View>: View {
    var fillsWidth = true
    @ViewBuilder let content: () -> Content

    var body: some View {
#if os(iOS)
        GlassEffectContainer(spacing: 8) { row }
#else
        row
#endif
    }

    private var row: some View {
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
