import SwiftUI
import Observation

/// Per-photo metadata modification state for the metadata tab. The report and the
/// styles check are derived from the working copy's original bytes; successful saves
/// mark the document so an already-injected photo is never injected twice in-session.
@MainActor @Observable final class MetadataState {
    var injectStandard = true
    var includeTexture = false
    var busy = false
    var errorMessage: String?
    var saved = false
    private(set) var report: MediaMetadataReport?

    private var requestedID: UUID?
    private var fileHasStyles = false
    private var fileHasStylesID: UUID?
    private var injectedDocuments = Set<UUID>()

    private static let supportedKinds: [PhotoMediaKind] = [.stillHEIC, .heicWithAuxiliaryData, .stillJPEG, .ultraHDRJPEG, .stillPNG]

    func stylesPresent(_ document: CardDocument) -> Bool {
        (fileHasStylesID == document.id && fileHasStyles) || injectedDocuments.contains(document.id)
    }

    func canInject(_ document: CardDocument) -> Bool {
        Self.supportedKinds.contains(document.metadata.kind) && !stylesPresent(document)
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
            fileHasStyles = false
            fileHasStylesID = nil
            return
        }
        let id = document.id
        let url = document.sourceURL
        let live = document.isLive
        Task {
            let loaded = await Task.detached(priority: .userInitiated) {
                (report: MediaMetadataReportReader.read(url: url, isLivePhoto: live),
                 styles: StyleInjection.stylesPresent(in: url))
            }.value
            guard !Task.isCancelled, id == requestedID else { return }
            report = loaded.report
            fileHasStyles = loaded.styles
            fileHasStylesID = id
        }
    }

    func save(document: CardDocument, updateOriginal: Bool) async {
        guard !busy, canInject(document), injectStandard else { return }
        busy = true
        defer { busy = false }
        let output = document.sourceURL.deletingLastPathComponent()
            .appendingPathComponent("Fuyao-\(UUID().uuidString).heic")
        let source = document.sourceURL
        let kind = document.metadata.kind
        let hdr = document.metadata.hdr
        let texture = includeTexture
        let name = document.originalName
        do {
            try await Task.detached(priority: .userInitiated) {
                try StyleInjection.inject(source: source, kind: kind, hdr: hdr,
                                          textureStyles: texture, grainSeedName: name, destination: output)
            }.value
            try await MetadataPhotoLibrary.save(photo: output, document: document,
                                                updateOriginal: updateOriginal, textureStyles: texture)
            try? FileManager.default.removeItem(at: output)
            injectedDocuments.insert(document.id)
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
