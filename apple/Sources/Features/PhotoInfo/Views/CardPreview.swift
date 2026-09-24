import SwiftUI
#if !os(macOS)
import UIKit
#endif

@MainActor @Observable final class CardPreviewState {
    var hdr = true
    var original = false
    var playing = false
    var fullScreen = false
    var liveDuration: TimeInterval?
    var liveStartedAt: Date?
    var isRendering = false

    var livePlaybackInterval: ClosedRange<Date>? {
        guard playing, let liveDuration, liveDuration > 0, let liveStartedAt else { return nil }
        return liveStartedAt...liveStartedAt.addingTimeInterval(liveDuration)
    }

    func finishLivePlayback() {
        playing = false
        liveStartedAt = nil
        liveDuration = nil
    }
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
            CardPreviewImage(document: document, hdr: controls.hdr, fullResolution: false,
                             original: controls.original, onRenderingChanged: { controls.isRendering = $0 })
            if controls.playing, let movie = document.sourceMovieURL {
                CardLivePhotoPreview(resources: [document.sourceURL, movie], movieURL: movie,
                                     durationLoaded: { controls.liveDuration = $0 },
                                     playbackStarted: { controls.liveStartedAt = $0 }) {
                    controls.finishLivePlayback()
                }
            }
        }
            .sheet(isPresented: $controls.fullScreen) {
                CardFullPreview(document: document, hdr: controls.hdr, original: controls.original)
            }
            .onChange(of: controls.playing) { _, playing in
                if !playing { controls.liveStartedAt = nil; controls.liveDuration = nil }
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

struct CardMediaControls: View {
    let document: CardDocument
    @Bindable var controls: CardPreviewState
    var body: some View {
        if document.isLive {
            Toggle(isOn: $controls.playing) {
                CardLivePlaybackIndicator(controls: controls)
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

struct CardLivePlaybackIndicator: View {
    let controls: CardPreviewState
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Group {
            if let interval = controls.livePlaybackInterval {
                TimelineView(.periodic(from: .now, by: reduceMotion ? 1 : 0.1)) { context in
                    let duration = interval.upperBound.timeIntervalSince(interval.lowerBound)
                    let progress = min(1, max(0, context.date.timeIntervalSince(interval.lowerBound) / duration))
                    Gauge(value: progress, in: 0...1) {
                        Text("card.live.preview")
                    } currentValueLabel: {
                        Image(systemName: "stop.fill").font(.system(size: 8, weight: .semibold))
                    }
                    .gaugeStyle(.accessoryCircular)
                }
            } else {
                Image(systemName: controls.playing ? "stop.circle" : "livephoto")
                    .contentTransition(reduceMotion ? .identity : .symbolEffect(.replace))
                    .animation(reduceMotion ? nil : .smooth(duration: 0.2), value: controls.playing)
            }
        }
        .frame(width: 22, height: 22)
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
    var onRenderingChanged: ((Bool) -> Void)?
    @State private var image: CGImage?
    @State private var loading = false
    @State private var error: String?
    @State private var renderingID: UUID?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            Color.black
            if let image {
                HDRImageView(image: image, enabled: hdr)
            }
            if loading {
                VStack(spacing: 8) {
                    Image(systemName: "photo.on.rectangle.angled")
                        .font(.title2)
                        .symbolEffect(.pulse, isActive: !reduceMotion)
                    Text("card.preview.loading").font(.footnote)
                }
                .foregroundStyle(.white)
            }
            if let error { Text(error).padding().foregroundStyle(.white).background(.black.opacity(0.8)) }
        }
        .accessibilityLabel(Text("card.preview"))
        .task(id: PreviewKey(id: document.id, card: document.card, hdr: hdr, full: fullResolution, original: original)) {
            let taskID = UUID()
            renderingID = taskID
            onRenderingChanged?(true)
            defer {
                if renderingID == taskID { onRenderingChanged?(false) }
            }
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
    @State private var resetVersion = 0
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            previewContent
                .background(.black)
                .navigationTitle("card.preview")
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) { Button("done", action: dismiss.callAsFunction) }
                    ToolbarItem(placement: .automatic) {
                        Button("card.preview.reset", systemImage: "1.magnifyingglass") {
#if os(macOS)
                            zoom = 1; committedZoom = 1
#else
                            resetVersion += 1
#endif
                        }
                        .buttonBorderShape(.circle)
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

    @ViewBuilder private var previewContent: some View {
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
        view.backgroundColor = .black
        scrollView.backgroundColor = .black
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
