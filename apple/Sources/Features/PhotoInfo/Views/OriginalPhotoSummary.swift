import SwiftUI

struct OriginalPhotoSummary: View {
    let document: CardDocument
    var showsBackdrop = true
    @State private var preview: CardPreviewState = {
        let value = CardPreviewState()
        value.original = true
        return value
    }()
    @State private var depthImage: CGImage?
    @State private var showsDepth = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            GeometryReader { geometry in
                ZStack {
                    if showsDepth, let depthImage {
                        Image(decorative: depthImage, scale: 1)
                            .resizable().scaledToFit()
                            .accessibilityLabel(Text("photo.depth.layer"))
                    } else {
                        CardPreviewSurface(document: document, controls: preview, showsBackdrop: false)
                    }
                }
                .frame(width: geometry.size.width, height: geometry.size.height)
            }
            .aspectRatio(4.0 / 3.0, contentMode: .fit)
            .background { PhotoImageShadow(aspectRatio: CGFloat(document.metadata.width) / CGFloat(max(1, document.metadata.height))) }

            PhotoInformationHeading(name: document.originalName,
                                    fileExtension: document.sourceURL.pathExtension,
                                    fileSize: document.metadata.fileSize)
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 8) { mediaButtons }
                VStack(alignment: .leading, spacing: 8) { mediaButtons }
            }
        }
        .background { if showsBackdrop { PhotoAmbientBackdrop(sourceURL: document.sourceURL, featherEdges: false) } }
        .task(id: document.sourceURL) {
            showsDepth = false
            preview.playing = false
            depthImage = nil
            let layer = await PortraitDepthLayerReader.readAsync(document.sourceURL)
            guard !Task.isCancelled else { return }
            depthImage = layer?.image
        }
        .onDisappear { preview.playing = false }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { preview.playing = false }
        }
    }

    @ViewBuilder private var mediaButtons: some View {
        if document.metadata.hdr {
            mediaButton("HDR", symbol: "sun.max", selected: preview.hdr && !showsDepth) {
                showsDepth = false
                preview.hdr.toggle()
            }
        }
        if document.isLive {
            mediaButton("card.live.preview", symbol: "livephoto", selected: preview.playing) {
                showsDepth = false
                preview.playing.toggle()
            }
        }
        if depthImage != nil {
            mediaButton("photo.depth.layer", symbol: "square.3.layers.3d", selected: showsDepth) {
                preview.playing = false
                showsDepth.toggle()
            }
        }
    }

    private func mediaButton(_ title: LocalizedStringKey, symbol: String, selected: Bool,
                             action: @escaping () -> Void) -> some View {
        Button(title, systemImage: symbol, action: action)
            .buttonStyle(.bordered)
            .tint(selected ? .accentColor : .secondary)
            .accessibilityAddTraits(selected ? .isSelected : [])
            .frame(minHeight: 44)
    }
}
