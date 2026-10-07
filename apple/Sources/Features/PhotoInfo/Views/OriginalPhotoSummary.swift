import SwiftUI

/// State shared between the pinned photo column and its accessory row.
@MainActor @Observable final class OriginalSummaryState {
    let preview: CardPreviewState = {
        let value = CardPreviewState()
        value.original = true
        return value
    }()
    var depthImage: CGImage?
    var showsDepth = false
}

/// The photo column of the original-photo summary; runs the depth lookup feeding the depth toggle.
struct OriginalSummaryPhoto: View {
    let document: CardDocument
    let state: OriginalSummaryState
    @Environment(\.scenePhase) private var scenePhase
    /// No zoom destination lives here; the surface only needs a namespace to satisfy its initializer.
    @Namespace private var idleZoom

    var body: some View {
        ZStack {
            if state.showsDepth, let depthImage = state.depthImage {
                Image(decorative: depthImage, scale: 1)
                    .resizable().scaledToFit()
                    .accessibilityLabel(Text("photo.depth.layer"))
            } else {
                CardPreviewSurface(document: document, controls: state.preview, zoom: idleZoom)
            }
        }
        .task(id: document.sourceURL) {
            state.showsDepth = false; state.preview.playing = false; state.depthImage = nil
            let layer = await PortraitDepthLayerReader.readAsync(document.sourceURL)
            guard !Task.isCancelled else { return }
            state.depthImage = layer?.image
        }
        .onDisappear { state.preview.playing = false }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { state.preview.playing = false }
        }
    }
}

/// The accessory row of the original-photo summary: file heading, media toggles, caller actions.
struct OriginalSummaryActions<Actions: View>: View {
    let document: CardDocument
    let metrics: PhotoPreviewMetrics
    let state: OriginalSummaryState
    var showsFileSummary = true
    @ViewBuilder var actions: Actions

    var body: some View {
        Group(subviews: actions) { extraActions in
            let tools = mediaTools
            let contentWidth = metrics.width - PhotoPageLayout.margin * 2
            let minimumTools = extraActions.count + (tools.isEmpty ? 0 : 1)
            let displaysFileSummary = showsFileSummary
                && contentWidth >= 128 + CGFloat(minimumTools) * PhotoPreviewMetrics.inlineToolWidth
            let room = contentWidth - (displaysFileSummary ? 128 : 0)
            let maximum = max(extraActions.count + 1, Int(room / PhotoPreviewMetrics.inlineToolWidth))
            let visibleCount = extraActions.count + tools.count <= maximum
                ? tools.count : max(0, maximum - extraActions.count - 1)
            HStack(spacing: 8) {
                if displaysFileSummary {
                    PhotoInformationHeading(name: document.originalName,
                        fileExtension: document.sourceURL.pathExtension, fileSize: document.metadata.fileSize)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                PhotoPreviewActionRow(fillsWidth: !displaysFileSummary) {
                    ForEach(extraActions) { $0 }
                    ForEach(Array(tools.prefix(visibleCount)), id: \.self) { tool in
                        mediaControl(tool)
                    }
                    if visibleCount < tools.count {
                        PhotoPreviewMenu(title: "card.more") {
                            ForEach(Array(tools.dropFirst(visibleCount)), id: \.self) { tool in
                                mediaMenuControl(tool)
                            }
                        }
                    }
                }
            }
        }
    }

    private enum MediaTool: Hashable { case hdr, live, depth }
    private var mediaTools: [MediaTool] {
        var result: [MediaTool] = []
        if document.metadata.hdr { result.append(.hdr) }
        if document.isLive { result.append(.live) }
        if state.depthImage != nil { result.append(.depth) }
        return result
    }

    private func mediaBinding(_ tool: MediaTool) -> Binding<Bool> {
        switch tool {
        case .hdr:
            Binding(get: { state.preview.hdr && !state.showsDepth }, set: { value in
                state.showsDepth = false; state.preview.hdr = value
            })
        case .live:
            Binding(get: { state.preview.playing }, set: { value in
                state.showsDepth = false; state.preview.playing = value
            })
        case .depth:
            Binding(get: { state.showsDepth }, set: { value in
                state.preview.playing = false; state.showsDepth = value
            })
        }
    }

    @ViewBuilder private func mediaControl(_ tool: MediaTool) -> some View {
        switch tool {
        case .hdr: CircularIconToggle("HDR", imageAsset: "HDR", isOn: mediaBinding(tool))
        case .live: CircularIconToggle("card.live.preview", systemImage: state.preview.playing ? "stop.circle" : "livephoto", isOn: mediaBinding(tool))
        case .depth: CircularIconToggle("photo.depth.layer", systemImage: "square.3.layers.3d", isOn: mediaBinding(tool))
        }
    }

    @ViewBuilder private func mediaMenuControl(_ tool: MediaTool) -> some View {
        switch tool {
        case .hdr:
            Toggle(isOn: mediaBinding(tool)) { Label { Text("HDR") } icon: { Image("HDR") } }
        case .live:
            Toggle("card.live.preview", systemImage: "livephoto", isOn: mediaBinding(tool))
        case .depth:
            Toggle("photo.depth.layer", systemImage: "square.3.layers.3d", isOn: mediaBinding(tool))
        }
    }
}
