import SwiftUI
import PhotosUI
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
    private(set) var savedToOriginal = false
    private(set) var report: MediaMetadataReport?

    private var requestedID: UUID?
    private var refreshRevision: UInt64 = 0
    private var fileCoverage: (photographic: Bool, texture: Bool) = (photographic: false, texture: false)
    private var fileCoverageID: UUID?
    private var injectedPhotographic = Set<UUID>()
    private var injectedTexture = Set<UUID>()
    private var updatedSources: [UUID: URL] = [:]

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
        refreshRevision &+= 1
        let revision = refreshRevision
        requestedID = document?.id
        includeTexture = false
        report = nil
        fileCoverage = (photographic: false, texture: false)
        fileCoverageID = nil
        guard let document else {
            return
        }
        let id = document.id
        let url = updatedSources[id] ?? document.sourceURL
        let live = document.isLive
        Task {
            let loaded = await Task.detached(priority: .userInitiated) {
                (report: MediaMetadataReportReader.read(url: url, isLivePhoto: live),
                 coverage: StyleInjection.stylesCoverage(in: url))
            }.value
            guard !Task.isCancelled, id == requestedID, revision == refreshRevision else { return }
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
        saved = false
        defer { busy = false }
        let output = document.sourceURL.deletingLastPathComponent()
            .appendingPathComponent("Fuyao-\(UUID().uuidString).heic")
        let source = updatedSources[document.id] ?? document.sourceURL
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
            if updateOriginal {
                // Keep the committed bytes in this document's private working directory so
                // a later style addition starts from the updated photo, not its old import.
                if let previous = updatedSources[document.id] {
                    try? FileManager.default.removeItem(at: previous)
                }
                updatedSources[document.id] = output
                if addPhotographic { injectedPhotographic.insert(document.id) }
                if addTexture { injectedTexture.insert(document.id) }
            } else {
                try? FileManager.default.removeItem(at: output)
            }
            savedToOriginal = updateOriginal
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


/// What the metadata page needs from a photo session: the cards session (shared
/// mode) and the metadata session (independent mode) both provide this.
@MainActor protocol MetadataPhotoSourcing: AnyObject, Observable {
    var documents: [CardDocument] { get }
    var selectedID: UUID? { get set }
    var current: CardDocument? { get }
    var busy: Bool { get }
    func open(_ results: [PHPickerResult]) async
    func openAssets(_ identifiers: [String]) async
}

extension CardSession: MetadataPhotoSourcing {}
extension MetadataSession: MetadataPhotoSourcing {}

/// The metadata tab's own photo session: independent from the cards session unless
/// sharing is enabled in Settings. Loading reuses the cards import pipeline.
@MainActor @Observable final class MetadataSession {
    static let selectionLimit = CardSession.selectionLimit
    var documents: [CardDocument] = []
    var selectedID: UUID?
    var busy = false
    var errorMessage: String?
    var current: CardDocument? { documents.first { $0.id == selectedID } }

    func open(_ results: [PHPickerResult]) async { await open(results.map(MetadataImportSource.picker)) }

    func openAssets(_ identifiers: [String]) async { await open(identifiers.map(MetadataImportSource.asset)) }

    private func open(_ items: [MetadataImportSource]) async {
        guard !busy, !items.isEmpty else { return }
        busy = true
        defer { busy = false }
        var imported: [CardDocument] = []
        var failures = 0
        for item in items.prefix(Self.selectionLimit) {
            do {
                try Task.checkCancellation()
                let folder = FileManager.default.temporaryDirectory
                    .appendingPathComponent("FuyaoMetadata-\(UUID().uuidString)", isDirectory: true)
                try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
                do {
                    let resources: PhotoSourceResources
                    switch item {
                    case .picker(let result): resources = try await PhotoSourceLoader.load(result, into: folder)
                    case .asset(let identifier):
                        guard let asset = CardPhotoLibrary.asset(identifier) else { throw CardError.permission }
                        resources = try await PhotoSourceLoader.load(asset, into: folder)
                    }
                    let metadata = try await CardImageProcessor.shared.read(resources.image, author: CardPreferences.shared.author)
                    imported.append(CardDocument(resources: resources, metadata: metadata))
                } catch {
                    try? FileManager.default.removeItem(at: folder)
                    throw error
                }
            } catch is CancellationError { break }
            catch { failures += 1 }
        }
        if !imported.isEmpty {
            documents = imported
            selectedID = imported.first?.id
        }
        if failures > 0 { errorMessage = String(format: String.localized("card.import.failed"), failures) }
    }

    func clear() {
        for document in documents { try? FileManager.default.removeItem(at: document.sourceURL.deletingLastPathComponent()) }
        documents = []
        selectedID = nil
    }
}

private enum MetadataImportSource {
    case picker(PHPickerResult)
    case asset(String)
}
