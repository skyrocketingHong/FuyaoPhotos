import AppKit
import SwiftUI
import Observation

// Only media ownership is synthetic. The mounted page, overlay, capture and GPU renderer are production sources.
@MainActor @Observable final class PhotoWorkspace {
    enum Tab: String, CaseIterable { case cards, metadata, colors }
    var selectedTab: Tab
    init(_ tab: Tab) { selectedTab = tab }
}

struct PhotoCard {}
struct CardOverlayRender {
    let image: CGImage
    let normalizedRect: CGRect
    let normalizedRadius: CGFloat
}
enum CardImageProcessor {
    static let shared = Self.processor
    case processor
    func preview(_ url: URL, card: PhotoCard, hdr: Bool, maxDimension: Int) async throws -> CGImage {
        throw ProbeError.check("unexpected business image decoder path")
    }
}

@MainActor @Observable final class CardSession {
    struct Document {
        let id = UUID()
        let sourceURL = URL(fileURLWithPath: "/synthetic-photo")
        let initialSDRPreview: CGImage?
    }
    var current: Document?
    private(set) var departure: PhotoDeparture?
    @ObservationIgnored private var playback = PhotoDeparturePlayback()
    private struct Capture {
        let owner: UUID
        let image: PhotoViewportCapture?
        let frame: @MainActor () -> CGRect
        let eligible: @MainActor () -> Bool
    }
    @ObservationIgnored private var captures = PhotoDepartureCaptureRegistry<Capture>()
    var lastRegisteredFrame: CGRect { captures.replacementCandidates.first?.frame() ?? .zero }
    init(image: CGImage) { current = Document(initialSDRPreview: image) }
    func prepareDeparture(_ image: CGImage, for documentID: UUID) {}
    func registerDepartureCapture(_ capture: PhotoViewportCapture?, frame: @escaping @MainActor () -> CGRect,
                                  owner: UUID, eligible: @escaping @MainActor () -> Bool) {
        guard eligible() else { return }
        captures.register(Capture(owner: owner, image: capture, frame: frame, eligible: eligible), ownerID: owner)
    }
    func removeDepartureCapture(owner: UUID) {
        captures.remove(ownerID: owner)
        if playback.cancel(ownerID: owner) { departure = nil }
    }
    func claimDeparture(id: UUID, owner: UUID) -> PhotoDeparture? {
        guard let departure, departure.id == id, playback.claim(id: id, ownerID: owner, now: .now) != nil else { return nil }
        return departure
    }
    func finishDeparture(id: UUID, owner: UUID) {
        if playback.finish(id: id, ownerID: owner) { departure = nil }
    }
    func revealDeparture(id: UUID, owner: UUID) -> Bool { playback.reveal(id: id, ownerID: owner, now: .now) }
    func isDepartureCurrent(id: UUID, owner: UUID) -> Bool {
        departure?.id == id && playback.isCurrent(id: id, ownerID: owner, now: .now)
    }
    func departureHidesImportContent(owner: UUID) -> Bool {
        departure != nil && playback.hidesImportContent(ownerID: owner, now: .now)
    }
    func close(owner: UUID) throws {
        guard let current, let capture = captures.capture(for: owner), capture.eligible(),
              let image = capture.image?(), let snapshot = PhotoDepartureSourceImage.freeze(image),
              !capture.frame().isEmpty else { throw ProbeError.check("source capture was not registered with geometry") }
        let now = ContinuousClock.now
        let ticket = playback.publish(ownerID: owner, now: now,
            lifetime: .milliseconds(PhotoMotionTokens.reassemblyLifetimeMillis))
        departure = PhotoDeparture(ticket: ticket, documentID: current.id, image: snapshot,
            sourceFrame: capture.frame(),
            targetDeadline: now.advanced(by: .milliseconds(PhotoMotionTokens.reassemblyTargetWaitMillis)))
        self.current = nil
    }
}

enum ProbeError: Error, CustomStringConvertible {
    case check(String)
    var description: String { switch self { case .check(let message): message } }
}
