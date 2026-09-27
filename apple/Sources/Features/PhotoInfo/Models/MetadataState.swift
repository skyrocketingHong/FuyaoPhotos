import SwiftUI
import Observation

/// Per-photo metadata modification state for the metadata tab. The report and the
/// styles coverage are derived from the working copy's original bytes; successful saves
/// mark the document per style generation so an already-injected layer is never
/// injected twice in-session.
@MainActor @Observable final class MetadataState {
    var injectStandard = true
    var includeTexture = false
    var busy = false
    var errorMessage: String?
    var saved = false
    private(set) var report: MediaMetadataReport?

    private var requestedID: UUID?
    private var fileCoverage: (photographic: Bool, texture: Bool) = (photographic: false, texture: false)
    private var fileCoverageID: UUID?
    private var injectedPhotographic = Set<UUID>()
    private var injectedTexture = Set<UUID>()

    private static let supportedKinds: [PhotoMediaKind] = [.stillHEIC, .heicWithAuxiliaryData, .stillJPEG, .ultraHDRJPEG, .stillPNG]

    func supportsInjection(_ document: CardDocument) -> Bool {
        Self.supportedKinds.contains(document.metadata.kind)
    }

    func coverage(_ document: CardDocument) -> (photographic: Bool, texture: Bool) {
        let file = fileCoverageID == document.id ? fileCoverage : (photographic: false, texture: false)
        return (file.photographic || injectedPhotographic.contains(document.id),
                file.texture || injectedTexture.contains(document.id))
    }

    func canAddPhotographic(_ document: CardDocument) -> Bool {
        supportsInjection(document) && !coverage(document).photographic
    }

    func canAddTexture(_ document: CardDocument) -> Bool {
        supportsInjection(document) && !coverage(document).texture
    }

    /// True when the save would write at least one missing styles layer.
    func hasPendingAdd(_ document: CardDocument) -> Bool {
        (canAddPhotographic(document) && injectStandard) || (canAddTexture(document) && includeTexture)
    }

    func unavailableReason(_ document: CardDocument) -> LocalizedStringKey {
        switch document.metadata.kind {
        case .motionJPEG, .hdrMotionJPEG: return "metadata.error.motion"
        case .xiaomiPortraitJPEG: return "metadata.error.xiaomi"
        default: return "metadata.error.format"
        }
    }

    func refresh(document: CardDocument?) {
        requestedID = document?.id
        includeTexture = false
        guard let document else {
            report = nil
            fileCoverage = (photographic: false, texture: false)
            fileCoverageID = nil
            return
        }
        let id = document.id
        let url = document.sourceURL
        let live = document.isLive
        Task {
            let loaded = await Task.detached(priority: .userInitiated) {
                (report: MediaMetadataReportReader.read(url: url, isLivePhoto: live),
                 coverage: StyleInjection.stylesCoverage(in: url))
            }.value
            guard !Task.isCancelled, id == requestedID else { return }
            report = loaded.report
            fileCoverage = loaded.coverage
            fileCoverageID = id
        }
    }

    func save(document: CardDocument, updateOriginal: Bool) async {
        guard !busy, supportsInjection(document) else { return }
        let addPhotographic = canAddPhotographic(document) && injectStandard
        let addTexture = canAddTexture(document) && includeTexture
        guard addPhotographic || addTexture else { return }
        busy = true
        defer { busy = false }
        let output = document.sourceURL.deletingLastPathComponent()
            .appendingPathComponent("Fuyao-\(UUID().uuidString).heic")
        let source = document.sourceURL
        let kind = document.metadata.kind
        let hdr = document.metadata.hdr
        let name = document.originalName
        do {
            try await Task.detached(priority: .userInitiated) {
                try StyleInjection.inject(source: source, kind: kind, hdr: hdr,
                                          addPhotographic: addPhotographic, addTexture: addTexture,
                                          grainSeedName: name, destination: output)
            }.value
            try await MetadataPhotoLibrary.save(photo: output, document: document,
                                                updateOriginal: updateOriginal,
                                                textureStyles: addTexture)
            try? FileManager.default.removeItem(at: output)
            if addPhotographic { injectedPhotographic.insert(document.id) }
            if addTexture { injectedTexture.insert(document.id) }
            saved = true
            refresh(document: document)
        } catch {
            try? FileManager.default.removeItem(at: output)
            errorMessage = Self.message(for: error)
        }
    }

    static func message(for error: Error) -> String {
        switch error {
        case StyleInjectionError.alreadyStyled:
            return String.localized("metadata.error.alreadyStyled")
        case StyleInjectionError.unsupportedSource(let reason):
            return String(format: String.localized("metadata.error.unsupported"), reason)
        case StyleInjectionError.encodingFailed(let reason):
            return String(format: String.localized("metadata.error.encoding"), reason)
        default:
            return CardSession.saveError(error, stage: .librarySave).localizedDescription
        }
    }
}
