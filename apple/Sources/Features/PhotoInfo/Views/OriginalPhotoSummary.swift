import SwiftUI

struct OriginalPhotoSummary<Actions: View>: View {
    let document: CardDocument
    let metrics: PhotoPreviewMetrics
    var showsFileSummary = true
    @ViewBuilder var actions: Actions
    @State private var preview: CardPreviewState = {
        let value = CardPreviewState()
        value.original = true
        return value
    }()
    @State private var depthImage: CGImage?
    @State private var showsDepth = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        PhotoPreviewStage(metrics: metrics,
            imageAspectRatio: CGFloat(document.metadata.width) / CGFloat(max(1, document.metadata.height))) {
            ZStack {
                if showsDepth, let depthImage {
                    Image(decorative: depthImage, scale: 1)
                        .resizable().scaledToFit()
                        .accessibilityLabel(Text("photo.depth.layer"))
                } else {
                    CardPreviewSurface(document: document, controls: preview)
                }
            }
        } accessories: {
            Group(subviews: actions) { extraActions in
                let tools = mediaTools
                let room = metrics.width - PhotoPageLayout.margin * 2 - (showsFileSummary ? 128 : 0)
                let maximum = max(extraActions.count + 1, Int(room / PhotoPreviewMetrics.inlineToolWidth))
                let visibleCount = extraActions.count + tools.count <= maximum
                    ? tools.count : max(0, maximum - extraActions.count - 1)
                HStack(spacing: 8) {
                    if showsFileSummary {
                        PhotoInformationHeading(name: document.originalName,
                            fileExtension: document.sourceURL.pathExtension, fileSize: document.metadata.fileSize)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    PhotoPreviewActionRow(fillsWidth: !showsFileSummary) {
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
        .task(id: document.sourceURL) {
            showsDepth = false; preview.playing = false; depthImage = nil
            let layer = await PortraitDepthLayerReader.readAsync(document.sourceURL)
            guard !Task.isCancelled else { return }
            depthImage = layer?.image
        }
        .onDisappear { preview.playing = false }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { preview.playing = false }
        }
    }
    private enum MediaTool: Hashable { case hdr, live, depth }
    private var mediaTools: [MediaTool] {
        var result: [MediaTool] = []
        if document.metadata.hdr { result.append(.hdr) }
        if document.isLive { result.append(.live) }
        if depthImage != nil { result.append(.depth) }
        return result
    }

    private func mediaBinding(_ tool: MediaTool) -> Binding<Bool> {
        switch tool {
        case .hdr:
            Binding(get: { preview.hdr && !showsDepth }, set: { value in
                showsDepth = false; preview.hdr = value
            })
        case .live:
            Binding(get: { preview.playing }, set: { value in
                showsDepth = false; preview.playing = value
            })
        case .depth:
            Binding(get: { showsDepth }, set: { value in
                preview.playing = false; showsDepth = value
            })
        }
    }

    @ViewBuilder private func mediaControl(_ tool: MediaTool) -> some View {
        switch tool {
        case .hdr: CircularIconToggle("HDR", imageAsset: "HDR", isOn: mediaBinding(tool))
        case .live: CircularIconToggle("card.live.preview", systemImage: preview.playing ? "stop.circle" : "livephoto", isOn: mediaBinding(tool))
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
