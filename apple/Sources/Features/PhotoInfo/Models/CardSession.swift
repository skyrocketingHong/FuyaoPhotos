import SwiftUI
import PhotosUI
import Photos
import MapKit
import CoreLocation
import os

@MainActor @Observable final class CardSession {
    static let selectionLimit = 50
    var documents: [CardDocument] = []
    var selectedID: UUID?
    var busy = false
    var progress = 0
    var total = 0
    var errorMessage: String?
    var savedCount: Int?
    var current: CardDocument? { documents.first { $0.id == selectedID } }
    var hasChanges: Bool { documents.contains { $0.hasChanges } }
    var canUpdateOriginals: Bool { !documents.isEmpty && documents.allSatisfy { $0.assetIdentifier != nil && !$0.isLive } }
    @ObservationIgnored private var geocoding: Task<Void, Never>?
    @ObservationIgnored private var locationRequest: MKReverseGeocodingRequest?
    @ObservationIgnored private let logger = Logger(subsystem: "ing.fuyaoskyrocket.photomap", category: "CardExport")

    func open(_ results: [PHPickerResult]) async { await open(results.map(CardImportSource.picker)) }

    func openAssets(_ identifiers: [String]) async { await open(identifiers.map(CardImportSource.asset)) }

    private func open(_ items: [CardImportSource]) async {
        guard !busy, !items.isEmpty else { return }
        busy = true; total = min(Self.selectionLimit, items.count); progress = 0; savedCount = nil
        defer { busy = false }
        var imported: [CardDocument] = []
        var failures = 0
        for item in items.prefix(Self.selectionLimit) {
            do {
                try Task.checkCancellation()
                let folder = FileManager.default.temporaryDirectory.appendingPathComponent("FuyaoCard-\(UUID().uuidString)", isDirectory: true)
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
            progress += 1
        }
        if Task.isCancelled {
            for document in imported { try? FileManager.default.removeItem(at: document.sourceURL.deletingLastPathComponent()) }
            return
        }
        if !imported.isEmpty {
            clear()
            documents = imported
            selectedID = imported.first?.id
            if CardPreferences.shared.resolveLocation { resolveLocations() }
        }
        if failures > 0 { errorMessage = String(format: String.localized("card.import.failed"), failures) }
    }

    func resolveLocations() {
        cancelLocationLookup()
        let candidates = documents
        geocoding = Task {
            for document in candidates {
                guard !Task.isCancelled, CardPreferences.shared.resolveLocation else { return }
                guard document.card[.location].isEmpty, let location = document.location,
                      let request = MKReverseGeocodingRequest(location: location) else { continue }
                let revision = document.locationRevision
                locationRequest = request
                let result = try? await request.mapItems
                if locationRequest === request { locationRequest = nil }
                guard !Task.isCancelled, CardPreferences.shared.resolveLocation,
                      documents.contains(where: { $0.id == document.id }), document.locationRevision == revision else { continue }
                document.card[.location] = result?.first?.addressRepresentations?.cityWithContext(.full) ?? ""
            }
        }
    }

    func cancelLocationLookup() {
        geocoding?.cancel()
        locationRequest?.cancel()
        locationRequest = nil
    }

    func save(options: CardSaveOptions) async {
        guard !busy, !documents.isEmpty else { return }
        cancelLocationLookup(); savedCount = nil
        busy = true; total = documents.count; progress = 0
        defer { busy = false }
        var saved = 0
        var failures: [String] = []
        for document in documents {
            var stage = CardError.imageEncoding
            var produced: [URL] = []
            var completed = false
            defer {
                if !completed { for url in produced { try? FileManager.default.removeItem(at: url) } }
            }
            do {
                switch document.metadata.kind {
                case .stillJPEG, .stillHEIC, .stillPNG, .ultraHDRJPEG, .heicWithAuxiliaryData: break
                default: throw CardError.unsupportedMedia
                }
                let output = document.sourceURL.deletingLastPathComponent().appendingPathComponent("Fuyao-\(UUID().uuidString).\(options.format.fileExtension)")
                produced.append(output)
                let identifier: String?
                if let original = document.sourceMovieURL { identifier = try await LivePhotoMovie.contentIdentifier(original) }
                else { identifier = nil }
                try await CardImageProcessor.shared.export(document.sourceURL, card: document.card, options: options,
                    hdr: document.metadata.hdr, to: output, live: document.isLive, liveIdentifier: identifier)
                var movie: URL?
                if let original = document.sourceMovieURL {
                    stage = .videoMetadata
                    let target = output.deletingPathExtension().appendingPathExtension("mov")
                    produced.append(target)
                    try await CardImageProcessor.shared.copyMovie(original, to: target, options: options)
                    movie = target
                }
                stage = .librarySave
                try await CardPhotoLibrary.save(photo: output, movie: movie, document: document, options: options)
                if let previous = document.exportURL {
                    try? FileManager.default.removeItem(at: previous)
                    try? FileManager.default.removeItem(at: previous.deletingPathExtension().appendingPathExtension("mov"))
                }
                document.exportURL = output
                document.savedCard = document.card
                completed = true
                saved += 1
            } catch {
                let systemError = error as NSError
                logger.error("Save failed: \(systemError.domain, privacy: .public) / \(systemError.code)")
                let message = Self.saveError(error,stage:stage).localizedDescription
                failures.append(documents.count == 1 ? message : "\(document.originalName)\n\(message)")
            }
            progress += 1
        }
        if saved > 0 {
            savedCount = saved
        }
        if !failures.isEmpty { errorMessage = failures.joined(separator: "\n") }
    }

    static func saveError(_ error: Error,stage: CardError) -> CardError {
        if let error = error as? CardError { return error }
        let value = error as NSError
        if value.domain == NSCocoaErrorDomain && value.code == NSFileWriteOutOfSpaceError { return .storageFull }
        if value.domain == PHPhotosErrorDomain {
            switch value.code {
            case PHPhotosError.Code.notEnoughSpace.rawValue: return .storageFull
            case PHPhotosError.Code.accessRestricted.rawValue,PHPhotosError.Code.accessUserDenied.rawValue: return .permission
            case PHPhotosError.Code.invalidResource.rawValue,PHPhotosError.Code.missingResource.rawValue: return .libraryResource
            default: return .librarySave
            }
        }
        return stage
    }

    func dismissError() {
        errorMessage = nil
    }

    func clear() {
        cancelLocationLookup()
        for document in documents { try? FileManager.default.removeItem(at: document.sourceURL.deletingLastPathComponent()) }
        documents = []; selectedID = nil; savedCount = nil
    }
}

private enum CardImportSource {
    case picker(PHPickerResult)
    case asset(String)
}
