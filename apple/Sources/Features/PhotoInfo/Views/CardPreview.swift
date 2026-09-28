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

    func finishLivePlayback() {
        playing = false
    }
}

struct CardPreviewSurface: View {
    let document: CardDocument
    @Bindable var controls: CardPreviewState
    var body: some View {
        ZStack {
            CardPreviewImage(document: document, hdr: controls.hdr, fullResolution: false,
                             original: controls.original, onRenderingChanged: { controls.isRendering = $0 })
            if controls.playing, let movie = document.sourceMovieURL {
                CardLivePhotoPreview(resources: [document.sourceURL, movie], movieURL: movie) {
                    controls.finishLivePlayback()
                }
            }
        }
            .sheet(isPresented: $controls.fullScreen) {
                CardFullPreview(document: document, hdr: controls.hdr, original: controls.original)
            }
            .onDisappear {
                controls.finishLivePlayback()
                controls.isRendering = false
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
                let rendered = try await CardImageProcessor.shared.preview(document.sourceURL, card: PhotoCard(),
                    hdr: requestedHDR, maxDimension: dimension)
                let overlay = original ? nil : try await CardImageProcessor.shared.previewOverlay(document.sourceURL,
                    card: document.card, maxDimension: dimension)
                try Task.checkCancellation()
                image = rendered
                cardOverlay = overlay
                effectiveHDR = requestedHDR
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
    @State private var resetVersion = 0
    @State private var isWide = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
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
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("done", action: dismiss.callAsFunction) }
                ToolbarItem(placement: .automatic) {
                    if !isWide {
                        Button("card.preview.reset", systemImage: "1.magnifyingglass", action: resetPreview)
                            .buttonBorderShape(.circle)
                    }
                }
            }
        }
#if !os(macOS)
        .interactiveDismissDisabled()
#endif
#if os(macOS)
        .frame(minWidth: 600, minHeight: 500)
#endif
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
        CardZoomablePreview(document: document, hdr: hdr, original: original, resetVersion: resetVersion)
#endif
    }
}

#if !os(macOS)
private struct CardZoomablePreview: UIViewControllerRepresentable {
    let document: CardDocument
    let hdr: Bool
    let original: Bool
    let resetVersion: Int

    func makeUIViewController(context: Context) -> CardZoomController {
        CardZoomController(preview: CardPreviewImage(document: document, hdr: hdr,
                                                     fullResolution: true, original: original))
    }

    func updateUIViewController(_ controller: CardZoomController, context: Context) {
        controller.preview = CardPreviewImage(document: document, hdr: hdr,
                                               fullResolution: true, original: original)
        controller.resetZoom(ifNeededFor: resetVersion)
    }
}

private final class CardZoomController: UIViewController, UIScrollViewDelegate {
    private let scrollView = UIScrollView()
    private let previewController: UIHostingController<CardPreviewImage>
    private var lastSize: CGSize = .zero
    private var lastResetVersion = 0

    var preview: CardPreviewImage {
        get { previewController.rootView }
        set { previewController.rootView = newValue }
    }

    init(preview: CardPreviewImage) {
        previewController = UIHostingController(rootView: preview)
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        scrollView.backgroundColor = .clear
        previewController.view.backgroundColor = .clear
        scrollView.minimumZoomScale = 1
        scrollView.maximumZoomScale = 8
        scrollView.bouncesZoom = true
        scrollView.delegate = self
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)
        NSLayoutConstraint.activate([
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.topAnchor.constraint(equalTo: view.topAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])

        addChild(previewController)
        scrollView.addSubview(previewController.view)
        previewController.didMove(toParent: self)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let size = scrollView.bounds.size
        guard size.width > 0, size.height > 0, size != lastSize else { return }
        lastSize = size
        scrollView.setZoomScale(1, animated: false)
        previewController.view.frame = CGRect(origin: .zero, size: size)
        scrollView.contentSize = size
    }

    func resetZoom(ifNeededFor version: Int) {
        guard version != lastResetVersion else { return }
        lastResetVersion = version
        scrollView.setZoomScale(1, animated: true)
    }

    func viewForZooming(in scrollView: UIScrollView) -> UIView? { previewController.view }
}
#endif
