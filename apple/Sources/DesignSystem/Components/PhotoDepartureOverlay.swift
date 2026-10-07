import SwiftUI

typealias PhotoViewportCapture = @MainActor () -> CGImage?

struct PhotoDeparture: Equatable {
    let id = UUID()
    let documentID: UUID
    let image: CGImage?
    let coversViewport: Bool
    static func == (lhs: Self, rhs: Self) -> Bool { lhs.id == rhs.id }
}

struct PhotoViewportFrameKey: PreferenceKey {
    static var defaultValue: CGRect { .zero }
    static func reduce(value: inout CGRect, nextValue: () -> CGRect) {
        let next = nextValue()
        if !next.isEmpty { value = next }
    }
}

/// Keeps only an SDR visual copy after the owning session has already discarded its private drafts.
struct PhotoDepartureOverlay: ViewModifier {
    let session: CardSession
    var imageCapture: (@MainActor () -> PhotoPreviewSnapshot?)?
    var viewportCapture: PhotoViewportCapture?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var viewport = CGRect.zero
    @State private var cached: Snapshot?
    @State private var departing: VisualCopy?
    @State private var captureOwner = UUID()

    private struct Snapshot {
        let documentID: UUID
        let sourceURL: URL?
        let image: CGImage
    }
    private struct VisualCopy {
        let id: UUID
        let snapshot: Snapshot
        let frame: CGRect
    }
    private struct SnapshotKey: Equatable {
        let documentID: UUID?
        let source: URL?
        let enabled: Bool
    }
    private var snapshotKey: SnapshotKey {
        SnapshotKey(documentID: session.current?.id, source: session.current?.sourceURL,
            enabled: !reduceMotion)
    }

    func body(content: Content) -> some View {
        content
            .onPreferenceChange(PhotoViewportFrameKey.self) { if !$0.isEmpty { viewport = $0 } }
            .overlay {
                GeometryReader { geometry in
                    if let departing, !reduceMotion, scenePhase == .active {
                        let origin = geometry.frame(in: .global).origin
                        TelegramDustView(image: departing.snapshot.image, sourceSize: departing.frame.size)
                            .id(departing.id)
                            .position(x: departing.frame.midX - origin.x, y: departing.frame.midY - origin.y)
                    }
                }
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
            .task(id: snapshotKey) {
                let key = snapshotKey
                guard key.enabled, let documentID = key.documentID, let source = key.source else { return }
                do {
                    let image: CGImage
                    if let initial = session.current?.initialSDRPreview {
                        image = initial
                    } else {
                        image = try await CardImageProcessor.shared.preview(source, card: PhotoCard(), hdr: false, maxDimension: 1440)
                    }
                    try Task.checkCancellation()
                    cached = Snapshot(documentID: documentID, sourceURL: source, image: image)
                    session.prepareDeparture(image, for: documentID)
                } catch { }
            }
            .onChange(of: session.departure) { _, departure in
                guard !reduceMotion, scenePhase == .active, let departure, !viewport.isEmpty else { return }
                let image = departure.coversViewport ? departure.image
                    : cached?.documentID == departure.documentID ? cached?.image : departure.image
                guard let image else { return }
                let scale = min(viewport.width / CGFloat(image.width), viewport.height / CGFloat(image.height))
                let size = CGSize(width: CGFloat(image.width) * scale, height: CGFloat(image.height) * scale)
                let frame = departure.coversViewport ? viewport
                    : CGRect(x: viewport.midX - size.width / 2, y: viewport.midY - size.height / 2,
                        width: size.width, height: size.height)
                departing = VisualCopy(id: departure.id,
                    snapshot: Snapshot(documentID: departure.documentID, sourceURL: nil, image: image), frame: frame)
            }
            .task(id: departing?.id) {
                guard let id = departing?.id else { return }
                do { try await Task.sleep(for: TelegramDustView.lifetime) } catch { return }
                if departing?.id == id { departing = nil }
                if session.current == nil { cached = nil }
            }
            .onChange(of: scenePhase) { _, phase in if phase != .active { departing = nil } }
            .onChange(of: reduceMotion) { _, reduced in if reduced { departing = nil; cached = nil } }
            .onAppear { session.registerDepartureCapture(captureViewport, owner: captureOwner) }
            .onDisappear {
                session.removeDepartureCapture(owner: captureOwner)
                departing = nil
                cached = nil
            }
    }

    private func captureViewport() -> CGImage? {
        if let viewportCapture { return viewportCapture() }
        guard let source = imageCapture?(), source.documentID == session.current?.id,
              source.sourceURL == session.current?.sourceURL else { return nil }
        let sdr = cached?.sourceURL == source.sourceURL ? cached?.image : session.current?.initialSDRPreview
        return source.render(in: viewport.size, sdrBase: sdr)
    }
}
