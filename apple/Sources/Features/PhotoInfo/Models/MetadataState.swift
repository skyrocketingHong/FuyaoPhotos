import SwiftUI
import PhotosUI
import Observation

/// Per-photo metadata modification state for the metadata tab. The report and the
/// styles coverage are derived from the working copy's original bytes; successful saves
/// mark the document per style generation so an already-injected layer is never
/// injected twice in-session.
@MainActor @Observable final class MetadataState {
    var injectStandard = false
    var includeTexture = false
    var options = CardSaveOptions(keepLocation: true)
    var hasMetadataChanges: Bool { !options.keepExif || !options.keepLocation || !options.keepCaptureTime }
    var busy = false
    var errorMessage: String?
    var saved = false
    private(set) var savedToOriginal = false
    private(set) var report: MediaMetadataReport?

    private var requestedID: UUID?
    private struct Draft {
        let standard: Bool
        let texture: Bool
        let options: CardSaveOptions
        var hasChanges: Bool { standard || texture || !options.keepExif || !options.keepLocation || !options.keepCaptureTime }
    }
    private var drafts: [UUID: Draft] = [:]
    private var refreshRevision: UInt64 = 0
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
        hasMetadataChanges || (canAddPhotographic(document) && injectStandard) || (canAddTexture(document) && includeTexture)
    }

    func hasChanges(for document: CardDocument) -> Bool {
        requestedID == document.id ? hasPendingAdd(document) : drafts[document.id]?.hasChanges == true
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
        if requestedID != document?.id {
            if let requestedID { drafts[requestedID] = Draft(standard: injectStandard, texture: includeTexture, options: options) }
            let draft = document.flatMap { drafts[$0.id] }
            options = draft?.options ?? CardSaveOptions(keepLocation: true)
            injectStandard = draft?.standard ?? false
            includeTexture = draft?.texture ?? false
        }
        requestedID = document?.id
        report = nil
        fileCoverage = (photographic: false, texture: false)
        fileCoverageID = nil
        guard let document else {
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
        guard addPhotographic || addTexture || hasMetadataChanges else { return }
        busy = true
        saved = false
        defer { busy = false }
        let fileExtension = addPhotographic || addTexture ? "heic" : document.sourceURL.pathExtension
        let output = document.sourceURL.deletingLastPathComponent()
            .appendingPathComponent("Fuyao-\(UUID().uuidString).\(fileExtension)")
        let source = document.sourceURL
        let sourceMovie = document.sourceMovieURL
        let movieOutput = output.deletingPathExtension().appendingPathExtension("mov")
        defer { try? FileManager.default.removeItem(at: movieOutput) }
        let kind = document.metadata.kind
        let hdr = document.metadata.hdr
        let name = document.originalName
        let options = options
        let cleaning = hasMetadataChanges
        do {
            try await Task.detached(priority: .userInitiated) {
                if addPhotographic || addTexture {
                    try StyleInjection.inject(source: source, kind: kind, hdr: hdr,
                                              addPhotographic: addPhotographic, addTexture: addTexture,
                                              grainSeedName: name, destination: output)
                    if let sourceMovie {
                        let styledMovie = output.deletingLastPathComponent().appendingPathComponent(UUID().uuidString + ".mov")
                        defer { try? FileManager.default.removeItem(at: styledMovie) }
                        if addTexture {
                            try await LivePhotoTextureMetadata.addIfNeeded(source: sourceMovie, photo: output, destination: styledMovie)
                            try await LivePhotoMovie.copy(from: styledMovie, to: movieOutput, options: options)
                        } else {
                            try await LivePhotoTextureMetadata.requirePhotographicTrack(sourceMovie)
                            try await LivePhotoMovie.copy(from: sourceMovie, to: movieOutput, options: options)
                        }
                    }
                    if cleaning {
                        let cleaned = output.deletingLastPathComponent().appendingPathComponent(UUID().uuidString + ".heic")
                        defer { try? FileManager.default.removeItem(at: cleaned) }
                        try MetadataImageWriter.write(output, to: cleaned, options: options)
                        _ = try FileManager.default.replaceItemAt(output, withItemAt: cleaned)
                    }
                } else {
                    try MetadataImageWriter.write(source, to: output, options: options)
                    if let sourceMovie { try await LivePhotoMovie.copy(from: sourceMovie, to: movieOutput, options: options) }
                }
            }.value
            let metadata = try await CardImageProcessor.shared.read(output, author: "")
            try await MetadataPhotoLibrary.save(photo: output, document: document,
                                                updateOriginal: updateOriginal,
                                                textureStyles: addTexture,
                                                pairedMovie: sourceMovie == nil ? nil : movieOutput, options: options)
            if updateOriginal {
                document.applyMetadataUpdate(source: output, metadata: metadata)
                try? FileManager.default.removeItem(at: source)
                if addPhotographic { injectedPhotographic.insert(document.id) }
                if addTexture { injectedTexture.insert(document.id) }
            } else {
                try? FileManager.default.removeItem(at: output)
            }
            savedToOriginal = updateOriginal
            saved = true
            drafts[document.id] = nil
            if requestedID == document.id {
                self.options = CardSaveOptions(keepLocation: true)
                injectStandard = false
                includeTexture = false
                refresh(document: document)
            }
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
