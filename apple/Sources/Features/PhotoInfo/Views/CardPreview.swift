import SwiftUI
#if !os(macOS)
import UIKit
#endif

@MainActor @Observable final class CardPreviewState {
    var hdr = true
    var original = false
    var playing = false
    var fullScreen = false
    var isRendering = false
    @ObservationIgnored var departureFrame: PhotoPreviewSnapshot?

    func finishLivePlayback() {
        playing = false
    }
}

struct CardPreviewSurface: View {
    let document: CardDocument
    @Bindable var controls: CardPreviewState
    var zoom: Namespace.ID
    var body: some View {
        ZStack {
            CardPreviewImage(document: document, hdr: controls.hdr, fullResolution: false,
                             original: controls.original, onRenderingChanged: { controls.isRendering = $0 },
                             onFrame: { controls.departureFrame = $0 })
            if controls.playing, let movie = document.sourceMovieURL {
                CardLivePhotoPreview(resources: [document.sourceURL, movie], movieURL: movie) {
                    controls.finishLivePlayback()
                }
            }
        }
#if os(iOS)
            // Zoom-transition source identity; the push itself is owned by the canvas,
            // outside the filmstrip's lazy pager.
            .matchedTransitionSource(id: document.id, in: zoom)
#endif
            .onDisappear {
                controls.finishLivePlayback()
                controls.isRendering = false
                if controls.departureFrame?.documentID == document.id { controls.departureFrame = nil }
            }
    }
}

struct CardNeighborPreview: View {
    let document: CardDocument
    let hdr: Bool
    var body: some View { CardPreviewImage(document:document,hdr:hdr,fullResolution:false) }
}

private struct PreviewKey: Equatable {
    let id: UUID
    let sourceURL: URL
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
    var onRenderingChanged: ((Bool) -> Void)?
    var onFrame: ((PhotoPreviewSnapshot) -> Void)?
    @State private var image: CGImage?
    @State private var cardOverlay: CardOverlayRender?
    @State private var loading = false
    @State private var error: String?
    @State private var renderingID: UUID?
    /// Whether the rendered image contains HDR pixels.
    @State private var effectiveHDR = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            Color.clear
            if let image {
                HDRImageView(image: image, enabled: effectiveHDR)
                    .overlay {
                        if !original, let cardOverlay {
                            GeometryReader { geometry in
                                let scale = min(geometry.size.width / CGFloat(image.width), geometry.size.height / CGFloat(image.height))
                                let width = CGFloat(image.width) * scale
                                let height = CGFloat(image.height) * scale
                                let rect = cardOverlay.normalizedRect
                                Image(decorative: cardOverlay.image, scale: 1)
                                    .resizable()
                                    .frame(width: rect.width * width, height: rect.height * height)
                                    .clipShape(.rect(cornerRadius: cardOverlay.normalizedRadius * width))
                                    .position(x: (geometry.size.width - width) / 2 + rect.midX * width,
                                              y: (geometry.size.height - height) / 2 + rect.midY * height)
                            }
                            .allowsHitTesting(false)
                        }
                    }
            }
            if loading {
                VStack(spacing: 8) {
                    Image(systemName: "photo.on.rectangle.angled")
                        .font(.title2)
                        .symbolEffect(.pulse, isActive: !reduceMotion)
                    Text("card.preview.loading").font(.footnote)
                }
                .foregroundStyle(.primary)
                .padding(12)
                .background(.regularMaterial, in: .rect(cornerRadius: 12))
            }
            if let error {
                Text(error).padding().foregroundStyle(.primary)
                    .background(.regularMaterial, in: .rect(cornerRadius: 12))
            }
        }
        .accessibilityLabel(Text("card.preview"))
        .task(id: PreviewKey(id: document.id, sourceURL: document.sourceURL, card: document.card, hdr: hdr, full: fullResolution, original: original)) {
            let taskID = UUID()
            renderingID = taskID
            onRenderingChanged?(true)
            defer {
                if renderingID == taskID { onRenderingChanged?(false) }
            }
            loading = true; error = nil
            let requestedHDR = hdr && document.metadata.hdr
            do {
                let dimension = fullResolution ? CGFloat(max(document.metadata.width, document.metadata.height)) : 1800
                let rendered: CGImage
                if !requestedHDR, !fullResolution, let initial = document.initialSDRPreview {
                    rendered = initial
                } else {
                    rendered = try await CardImageProcessor.shared.preview(document.sourceURL, card: PhotoCard(),
                        hdr: requestedHDR, maxDimension: dimension)
                }
                let overlay = original ? nil : try await CardImageProcessor.shared.previewOverlay(document.sourceURL,
                    card: document.card, maxDimension: dimension)
                try Task.checkCancellation()
                image = rendered
                cardOverlay = overlay
                effectiveHDR = requestedHDR
                onFrame?(PhotoPreviewSnapshot(documentID: document.id, sourceURL: document.sourceURL,
                    image: rendered, overlay: overlay))
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

struct CardFullPreview: View {
    let document: CardDocument
    let hdr: Bool
    let original: Bool
    var close: (() -> Void)? = nil
    @State private var zoom = 1.0
    @State private var committedZoom = 1.0
    @State private var resetVersion = 0
    @State private var isWide = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        GeometryReader { geometry in
            if geometry.size.width >= 800 {
                HStack(spacing: 0) {
                    previewContent
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    Divider()
                    previewInspector
                        .frame(width: 220)
                }
            } else {
                previewContent
            }
        }
        .background(PhotoPreviewTheme.surface)
        .onGeometryChange(for: Bool.self) { $0.size.width >= 800 } action: { isWide = $0 }
        .navigationTitle("card.preview")
#if os(iOS)
        .toolbarVisibility(.visible, for: .navigationBar)
#endif
        .toolbar {
#if os(macOS)
            ToolbarItem(placement: .cancellationAction) {
                Button("close", systemImage: "xmark") { if let close { close() } else { dismiss() } }
                    .labelStyle(.iconOnly)
            }
#endif
            ToolbarItem(placement: .automatic) {
                if !isWide {
                    Button("card.preview.reset", systemImage: "1.magnifyingglass", action: resetPreview)
                        .buttonBorderShape(.circle)
                }
            }
        }
    }

    private var previewInspector: some View {
        Form {
            Section("card.preview.status") {
                LabeledContent("card.preview.source") {
                    Text(original ? "card.preview.original" : "card.preview.edited")
                }
                LabeledContent("photo.dimensions") {
                    Text("\(document.metadata.width) × \(document.metadata.height)")
                        .monospacedDigit()
                }
                if document.metadata.hdr {
                    LabeledContent("HDR") {
                        Text(hdr ? "photo.info.yes" : "photo.info.no")
                    }
                }
#if os(macOS)
                LabeledContent("card.preview.zoom") {
                    Text(zoom, format: .percent.precision(.fractionLength(0)))
                        .monospacedDigit()
                }
#endif
            }
            Section {
                Button("card.preview.reset", systemImage: "1.magnifyingglass", action: resetPreview)
            }
        }
        .photoPageForm()
    }

    private func resetPreview() {
#if os(macOS)
        zoom = 1; committedZoom = 1
#else
        resetVersion += 1
#endif
    }

    private var previewContent: some View {
        zoomableContent
            .background { PhotoImageShadow(aspectRatio: CGFloat(document.metadata.width) / CGFloat(max(1, document.metadata.height))) }
            .background { PhotoWorkspaceBackdrop(sourceURL: document.sourceURL) }
    }

    @ViewBuilder private var zoomableContent: some View {
#if os(macOS)
        GeometryReader { geometry in
            ScrollView([.horizontal, .vertical]) {
                CardPreviewImage(document: document, hdr: hdr, fullResolution: true, original: original)
                    .frame(width: geometry.size.width * zoom, height: geometry.size.height * zoom)
            }
            .gesture(MagnifyGesture().onChanged { value in
                zoom = min(8, max(1, committedZoom * value.magnification))
            }.onEnded { _ in committedZoom = zoom })
        }
#else
        NativeZoomablePreview(resetVersion: resetVersion) {
            CardPreviewImage(document: document, hdr: hdr, fullResolution: true, original: original)
        }
#endif
    }
}
