import SwiftUI

typealias PhotoViewportCapture = @MainActor () -> CGImage?

struct PhotoDeparture: Equatable {
    let ticket: PhotoDeparturePlayback.Ticket
    let documentID: UUID
    let image: CGImage
    let coversViewport: Bool
    var id: UUID { ticket.id }
    static func == (lhs: Self, rhs: Self) -> Bool { lhs.id == rhs.id }
}

extension EnvironmentValues {
    @Entry var photoImportContentVisible = true
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
    let page: PhotoWorkspace.Tab
    var imageCapture: (@MainActor () -> PhotoPreviewSnapshot?)?
    var viewportCapture: PhotoViewportCapture?
    @Environment(PhotoWorkspace.self) private var workspace
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var appeared = false
    @State private var viewport = CGRect.zero
    @State private var canvasSize = CGSize.zero
    @State private var cached: Snapshot?
    @State private var departing: VisualCopy?
    @State private var revealedID: UUID?
    @State private var captureOwner = UUID()

    private struct Snapshot {
        let documentID: UUID
        let sourceURL: URL
        let image: CGImage
    }
    private struct VisualCopy {
        let ticket: PhotoDeparturePlayback.Ticket
        let image: CGImage
        let frame: CGRect
        let rendererPermit = DustPlaybackPermit()
    }
    private struct SnapshotKey: Equatable {
        let documentID: UUID?
        let source: URL?
        let enabled: Bool
    }
    private var eligible: Bool {
        appeared && workspace.selectedTab == page && scenePhase == .active && !reduceMotion
    }
    private var importContentVisible: Bool {
        !eligible || (revealedID != nil && revealedID == session.departure?.id)
            || !session.departureHidesImportContent(owner: captureOwner)
    }
    private var snapshotKey: SnapshotKey {
        SnapshotKey(documentID: session.current?.id, source: session.current?.sourceURL, enabled: eligible)
    }

    func body(content: Content) -> some View {
        content
            .environment(\.photoImportContentVisible, importContentVisible)
            .onPreferenceChange(PhotoViewportFrameKey.self) { if !$0.isEmpty { viewport = $0 } }
            .onGeometryChange(for: CGSize.self) { $0.size } action: { size in
                if canvasSize != .zero, canvasSize != size, let ticket = departing?.ticket { finish(ticket.id) }
                canvasSize = size
            }
            .overlay {
                GeometryReader { geometry in
                    if let departing, eligible,
                       session.isDepartureCurrent(id: departing.ticket.id, owner: captureOwner) {
                        let origin = geometry.frame(in: .global).origin
                        TelegramDustView(image: departing.image, sourceSize: departing.frame.size,
                            playbackPermit: departing.rendererPermit,
                            onReveal: { reveal(departing.ticket.id) },
                            onUnavailable: { finish(departing.ticket.id) })
                            .id(departing.ticket.id)
                            .position(x: departing.frame.midX - origin.x, y: departing.frame.midY - origin.y)
                    }
                }
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
            .task(id: snapshotKey) {
                let key = snapshotKey
                guard key.enabled, let documentID = key.documentID, let source = key.source else { cached = nil; return }
                do {
                    let image: CGImage
                    if let initial = session.current?.initialSDRPreview { image = initial }
                    else { image = try await CardImageProcessor.shared.preview(source, card: PhotoCard(), hdr: false, maxDimension: 1440) }
                    try Task.checkCancellation()
                    guard eligible else { return }
                    cached = Snapshot(documentID: documentID, sourceURL: source, image: image)
                    session.prepareDeparture(image, for: documentID)
                } catch { }
            }
            .onChange(of: session.departure) { _, departure in receive(departure) }
            .task(id: departing?.ticket.id) {
                guard let ticket = departing?.ticket else { return }
                do {
                    try await ContinuousClock().sleep(until: ticket.expiresAt)
                } catch {
                    finish(ticket.id)
                    return
                }
                finish(ticket.id)
            }
            .onChange(of: eligible) { _, _ in updateEligibility() }
            .onAppear {
                if let ticket = departing?.ticket { finish(ticket.id) }
                appeared = true
                updateEligibility()
            }
            .onDisappear {
                appeared = false
                deactivate()
            }
    }

    private func receive(_ value: PhotoDeparture?) {
        guard let value else {
            if session.departure == nil {
                if let ticket = departing?.ticket { finish(ticket.id) }
                revealedID = nil
            }
            return
        }
        guard session.departure?.id == value.id else { return }
        if let previous = departing?.ticket, previous.id != value.id { finish(previous.id) }
        guard eligible, !viewport.isEmpty else {
            session.finishDeparture(id: value.id, owner: captureOwner)
            return
        }
        guard let event = session.claimDeparture(id: value.id, owner: captureOwner) else { return }
        let image = event.coversViewport ? event.image
            : cached?.documentID == event.documentID ? cached?.image ?? event.image : event.image
        let scale = min(viewport.width / CGFloat(image.width), viewport.height / CGFloat(image.height))
        let size = CGSize(width: CGFloat(image.width) * scale, height: CGFloat(image.height) * scale)
        let frame = event.coversViewport ? viewport
            : CGRect(x: viewport.midX - size.width / 2, y: viewport.midY - size.height / 2,
                width: size.width, height: size.height)
        revealedID = nil
        departing = VisualCopy(ticket: event.ticket, image: image, frame: frame)
    }

    private func finish(_ id: UUID) {
        if departing?.ticket.id == id { departing = nil }
        if revealedID == id { revealedID = nil }
        session.finishDeparture(id: id, owner: captureOwner)
    }

    private func reveal(_ id: UUID) {
        guard departing?.ticket.id == id, session.revealDeparture(id: id, owner: captureOwner) else { return }
        revealedID = id
    }

    private func updateEligibility() {
        if eligible {
            session.registerDepartureCapture(captureViewport, owner: captureOwner, eligible: { eligible })
        } else { deactivate() }
    }

    private func deactivate() {
        session.removeDepartureCapture(owner: captureOwner)
        if let ticket = departing?.ticket { finish(ticket.id) }
        revealedID = nil
        cached = nil
    }

    private func captureViewport() -> CGImage? {
        guard eligible else { return nil }
        if let viewportCapture { return viewportCapture() }
        guard let source = imageCapture?(), source.documentID == session.current?.id,
              source.sourceURL == session.current?.sourceURL else { return nil }
        let sdr = cached?.sourceURL == source.sourceURL ? cached?.image : session.current?.initialSDRPreview
        return source.render(in: viewport.size, sdrBase: sdr)
    }
}
