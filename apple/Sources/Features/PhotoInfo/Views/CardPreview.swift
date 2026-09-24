import SwiftUI

@MainActor @Observable final class CardPreviewState {
    var hdr = true
    var original = false
    var playing = false
    var fullScreen = false
}

struct CardPreview: View {
    let document: CardDocument
    @State private var controls = CardPreviewState()
    var body: some View {
        CardPreviewSurface(document: document, controls: controls)
            .overlay(alignment: .bottomTrailing) {
                HStack { CardMediaControls(document: document, controls: controls) }
                    .buttonStyle(.glass).buttonBorderShape(.circle)
                    .labelStyle(.iconOnly).padding(12)
            }
    }
}

struct CardPreviewSurface: View {
    let document: CardDocument
    @Bindable var controls: CardPreviewState
    var body: some View {
        ZStack {
            CardPreviewImage(document: document, hdr: controls.hdr, fullResolution: false, original: controls.original)
            if controls.playing, let movie = document.sourceMovieURL {
                CardLivePhotoPreview(resources: [document.sourceURL, movie]) { controls.playing = false }
            }
        }
            .sheet(isPresented: $controls.fullScreen) {
                CardFullPreview(document: document, hdr: controls.hdr, original: controls.original)
            }
            .onDisappear { controls.playing = false }
    }
}

struct CardNeighborPreview: View {
    let document: CardDocument
    let hdr: Bool
    var body: some View { CardPreviewImage(document:document,hdr:hdr,fullResolution:false) }
}

struct CardMediaControls: View {
    let document: CardDocument
    @Bindable var controls: CardPreviewState
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var body: some View {
        if document.isLive {
            Toggle(isOn: $controls.playing) {
                Image(systemName: controls.playing ? "stop.circle" : "livephoto")
                    .contentTransition(reduceMotion ? .identity : .symbolEffect(.replace))
                    .animation(reduceMotion ? nil : .smooth(duration: 0.2), value: controls.playing)
                    .frame(width: 20, height: 20)
            }
                .toggleStyle(.button).buttonBorderShape(.circle).padding(6)
                .accessibilityLabel(Text("card.live.preview"))
                .help(Text("card.live.preview"))
        }
        if document.metadata.hdr {
            Toggle(isOn: $controls.hdr) { Label { Text("HDR") } icon: { Image("HDR") } }
                .toggleStyle(.button).buttonBorderShape(.circle).padding(6)
        }
        Toggle("card.compare",systemImage:"square.on.square",isOn:$controls.original).toggleStyle(.button)
            .buttonBorderShape(.circle).padding(6)
        CircularIconButton("card.preview.full", systemImage: "arrow.up.left.and.arrow.down.right") {
            controls.playing = false
            controls.fullScreen = true
        }
    }
}

private struct PreviewKey: Equatable {
    let id: UUID
    let card: PhotoCard
    let hdr: Bool
    let full: Bool
    let original: Bool
}

private struct CardPreviewImage: View {
    let document: CardDocument
    let hdr: Bool
    let fullResolution: Bool
    var original = false
    @State private var image: CGImage?
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        ZStack {
            Color.black
            if let image {
                HDRImageView(image: image, enabled: hdr)
            }
            if loading { ProgressView("card.preview.loading").tint(.white).foregroundStyle(.white) }
            if let error { Text(error).padding().foregroundStyle(.white).background(.black.opacity(0.8)) }
        }
        .accessibilityLabel(Text("card.preview"))
        .task(id: PreviewKey(id: document.id, card: document.card, hdr: hdr, full: fullResolution, original: original)) {
            loading = true; error = nil
            do {
                let rendered = try await CardImageProcessor.shared.preview(document.sourceURL, card: original ? PhotoCard() : document.card,
                    hdr: hdr && document.metadata.hdr, maxDimension: fullResolution ? CGFloat(max(document.metadata.width, document.metadata.height)) : 1800)
                try Task.checkCancellation()
                image = rendered
                loading = false
            } catch is CancellationError { }
            catch {
                guard !Task.isCancelled else { return }
                self.error = (error as? CardError)?.localizedDescription ?? CardError.invalidImage.localizedDescription
                loading = false
            }
        }
    }
}

private struct CardFullPreview: View {
    let document: CardDocument
    let hdr: Bool
    let original: Bool
    @State private var zoom = 1.0
    @State private var committedZoom = 1.0
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            GeometryReader { geometry in
                ScrollView([.horizontal, .vertical]) {
                    CardPreviewImage(document: document, hdr: hdr, fullResolution: true, original: original)
                        .frame(width: geometry.size.width * zoom, height: geometry.size.height * zoom)
                }
                .gesture(MagnifyGesture().onChanged { value in
                    zoom = min(8, max(1, committedZoom * value.magnification))
                }.onEnded { _ in committedZoom = zoom })
            }
            .background(.black)
            .navigationTitle("card.preview")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("done", action: dismiss.callAsFunction) }
                ToolbarItem(placement: .automatic) {
                    Button("card.preview.reset", systemImage: "1.magnifyingglass") { zoom = 1; committedZoom = 1 }
                        .buttonBorderShape(.circle)
                }
            }
        }
#if os(macOS)
        .frame(minWidth: 600, minHeight: 500)
#endif
    }
}
