import SwiftUI

typealias PhotoViewportCapture = @MainActor () -> CGImage?

struct PhotoDeparture: Equatable {
    let ticket: PhotoDeparturePlayback.Ticket
    let documentID: UUID
    let image: CGImage
    let sourceFrame: CGRect
    let targetDeadline: ContinuousClock.Instant?
    var id: UUID { ticket.id }
    static func == (lhs: Self, rhs: Self) -> Bool { lhs.id == rhs.id }
}

enum PhotoDepartureSourceImage {
    static func freeze(_ image: CGImage) -> CGImage? {
        guard image.width > 0, image.height > 0, let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) else { return nil }
        let scale = min(1, sqrt(Double(PhotoMotionTokens.snapshotPixelLimit) / (Double(image.width) * Double(image.height))))
        let width = max(1, Int((Double(image.width) * scale).rounded()))
        let height = max(1, Int((Double(image.height) * scale).rounded()))
        guard let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8,
            bytesPerRow: 0, space: colorSpace, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        return context.makeImage()
    }
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
    let captureOwner: UUID
    var imageCapture: (@MainActor () -> PhotoPreviewSnapshot?)?
    var viewportCapture: PhotoViewportCapture?
    @Environment(PhotoWorkspace.self) private var workspace
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var appeared = false
    @State private var viewport = CGRect.zero
    @State private var canvasFrame = CGRect.zero
    @State private var cached: Snapshot?
    @State private var departing: VisualCopy?
    @State private var revealedID: UUID?
    @State private var registeredSession: CardSession?
    @State private var targetCapture = PhotoImportTransitionCapture()

    private struct Snapshot {
        let documentID: UUID
        let sourceURL: URL
        let image: CGImage
    }
    private struct VisualCopy {
        let ticket: PhotoDeparturePlayback.Ticket
        let session: CardSession
        let image: CGImage
        let frame: CGRect
        let targetDeadline: ContinuousClock.Instant?
        var target: PhotoImportTransitionTarget?
        var playbackOrigin: CGPoint?
        let rendererPermit = DustPlaybackPermit()
    }
    private struct SnapshotKey: Equatable {
        let documentID: UUID?
        let source: URL?
        let enabled: Bool
        let sessionID: ObjectIdentifier
    }
    private var eligible: Bool {
        appeared && workspace.selectedTab == page && scenePhase == .active && !reduceMotion
    }
    private var importContentVisible: Bool {
        !eligible || (revealedID != nil && revealedID == session.departure?.id)
            || !session.departureHidesImportContent(owner: captureOwner)
    }
    private var snapshotKey: SnapshotKey {
        SnapshotKey(documentID: session.current?.id, source: session.current?.sourceURL,
                    enabled: eligible, sessionID: ObjectIdentifier(session))
    }

    func body(content: Content) -> some View {
        content
            .environment(\.photoImportContentVisible, importContentVisible)
            .environment(\.photoImportTransitionCapture, targetCapture)
            .onPreferenceChange(PhotoViewportFrameKey.self) { if !$0.isEmpty { viewport = $0 } }
            .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { frame in
                if canvasFrame != .zero, canvasFrame != frame, let departing {
                    if departing.targetDeadline == nil || (departing.target != nil &&
                        (abs(canvasFrame.width - frame.width) > 0.5 ||
                         abs(canvasFrame.minX - frame.minX) > 0.5 || abs(canvasFrame.minY - frame.minY) > 0.5)) {
                        finish(departing.ticket.id)
                    }
                }
                canvasFrame = frame
            }
            .overlay {
                GeometryReader { geometry in
                    if let departing, eligible,
                       departing.session === session,
                       departing.session.isDepartureCurrent(id: departing.ticket.id, owner: captureOwner) {
                        let origin = departing.playbackOrigin ?? geometry.frame(in: .global).origin
                        let sourceFrame = departing.frame.offsetBy(dx: -origin.x, dy: -origin.y)
                        if departing.targetDeadline != nil {
                            if let target = departing.target {
                                PhotoReassemblyView(sourceImage: departing.image, sourceFrame: sourceFrame,
                                    targetImage: target.image,
                                    targetFrame: target.frame.offsetBy(dx: -origin.x, dy: -origin.y),
                                    canvasSize: geometry.size,
                                    onFinished: { finish(departing.ticket.id) },
                                    onUnavailable: { finish(departing.ticket.id) })
                                    .id(departing.ticket.id)
                            } else {
                                Image(decorative: departing.image, scale: 1)
                                    .resizable()
                                    .frame(width: sourceFrame.width, height: sourceFrame.height)
                                    .position(x: sourceFrame.midX, y: sourceFrame.midY)
                            }
                        } else {
                            TelegramDustView(image: departing.image, sourceSize: departing.frame.size,
                                playbackPermit: departing.rendererPermit,
                                onReveal: { reveal(departing.ticket.id) },
                                onUnavailable: { finish(departing.ticket.id) })
                                .id(departing.ticket.id)
                                .position(x: sourceFrame.midX, y: sourceFrame.midY)
                        }
                    }
                }
                .transaction { transaction in
                    transaction.animation = nil
                    transaction.disablesAnimations = true
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
            .onChange(of: targetCapture.target?.requestID) { _, _ in receiveTarget() }
            .onChange(of: targetCapture.failedID) { _, id in if let id { finish(id) } }
            .onChange(of: targetCapture.liveContentFrame) { _, frame in
                if let copy = departing, let target = copy.target, let frame,
                   abs(frame.minX - target.contentFrame.minX) > 0.5 ||
                   abs(frame.minY - target.contentFrame.minY) > 0.5 ||
                   abs(frame.width - target.contentFrame.width) > 0.5 ||
                   abs(frame.height - target.contentFrame.height) > 0.5 {
                    finish(copy.ticket.id)
                }
            }
            .task(id: departing?.ticket.id) {
                guard let copy = departing else { return }
                let ticket = copy.ticket
                do {
                    if let deadline = copy.targetDeadline {
                        try await ContinuousClock().sleep(until: deadline)
                        guard departing?.ticket.id == ticket.id else { return }
                        if departing?.target == nil { finish(ticket.id); return }
                    }
                    try await ContinuousClock().sleep(until: ticket.expiresAt)
                } catch { return }
                finish(ticket.id)
            }
            .onChange(of: eligible) { _, _ in updateEligibility() }
            .onChange(of: ObjectIdentifier(session)) { _, _ in updateEligibility() }
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
        guard eligible, !value.sourceFrame.isEmpty else {
            session.finishDeparture(id: value.id, owner: captureOwner)
            return
        }
        guard let event = session.claimDeparture(id: value.id, owner: captureOwner) else { return }
        withoutAnimation {
            revealedID = nil
            departing = VisualCopy(ticket: event.ticket, session: session, image: event.image,
                frame: event.sourceFrame, targetDeadline: event.targetDeadline)
            if let deadline = event.targetDeadline { targetCapture.begin(id: event.id, deadline: deadline) }
        }
    }

    private func receiveTarget() {
        guard let target = targetCapture.target, let copy = departing,
              target.requestID == copy.ticket.id, copy.target == nil,
              eligible, copy.session === session,
              copy.session.isDepartureCurrent(id: copy.ticket.id, owner: captureOwner) else { return }
        withoutAnimation {
            departing?.target = target
            departing?.playbackOrigin = canvasFrame.origin
        }
    }

    private func finish(_ id: UUID) {
        withoutAnimation {
            let ownerSession = departing?.ticket.id == id ? departing?.session : session
            if departing?.ticket.id == id { departing = nil }
            if revealedID == id { revealedID = nil }
            targetCapture.cancel(id: id)
            ownerSession?.finishDeparture(id: id, owner: captureOwner)
        }
    }

    private func withoutAnimation(_ operation: () -> Void) {
        var transaction = Transaction()
        transaction.animation = nil
        transaction.disablesAnimations = true
        withTransaction(transaction, operation)
    }

    private func reveal(_ id: UUID) {
        guard departing?.ticket.id == id, session.revealDeparture(id: id, owner: captureOwner) else { return }
        revealedID = id
    }

    private func updateEligibility() {
        if let registeredSession, registeredSession !== session {
            registeredSession.removeDepartureCapture(owner: captureOwner)
            if let ticket = departing?.ticket { finish(ticket.id) }
            cached = nil
            targetCapture.cancel()
            self.registeredSession = nil
        }
        if eligible {
            session.registerDepartureCapture(captureViewport, frame: { viewport }, owner: captureOwner, eligible: { eligible })
            registeredSession = session
        } else { deactivate() }
    }

    private func deactivate() {
        registeredSession?.removeDepartureCapture(owner: captureOwner)
        registeredSession = nil
        if let ticket = departing?.ticket { finish(ticket.id) }
        targetCapture.cancel()
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
