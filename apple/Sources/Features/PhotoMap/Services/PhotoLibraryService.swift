import Foundation
import Observation
import MapKit
@preconcurrency import Photos
import PhotoMapCore
#if os(macOS)
import AppKit
typealias NativeImage = NSImage
#else
import UIKit
typealias NativeImage = UIImage
#endif

/// UI state stays on the main actor; library enumeration and spatial queries do not.
@MainActor @Observable
final class PhotoLibraryService: NSObject, PHPhotoLibraryChangeObserver {
    static let shared = PhotoLibraryService()
    private(set) var isLoading = false
    private(set) var errorMessage: String?
    private(set) var photosEarliestYear: Int?
    private(set) var photoCount = 0
    private(set) var videoCount = 0
    private(set) var indexVersion: UInt64 = 0
    private(set) var authorizationStatus = PHPhotoLibrary.authorizationStatus(for: .readWrite)
    let thumbnails = PhotoThumbnailStore()
    @ObservationIgnored private let index = PhotoSpatialIndex()
    @ObservationIgnored private let worker = PhotoLibraryIndexWorker()
    @ObservationIgnored private var loaded = false
    @ObservationIgnored private var permissionGeneration: UInt64 = 0
    @ObservationIgnored private var loadTask: Task<Void, Never>?
    @ObservationIgnored private var changeTask: Task<Void, Never>?
    @ObservationIgnored private var authorizationTask: Task<Void, Never>?
    @ObservationIgnored private var indexedTimeZone = TimeZone.current.identifier

    override init() { super.init(); PHPhotoLibrary.shared().register(self) }
    deinit { PHPhotoLibrary.shared().unregisterChangeObserver(self) }
    private var hasAccess: Bool { authorizationStatus == .authorized || authorizationStatus == .limited }

    func requestAuthorization() async -> Bool {
        if authorizationStatus == .notDetermined {
            authorizationStatus = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        } else { await refreshAuthorization() }
        errorMessage = hasAccess ? nil : String.localized("permission.photo.library.description")
        return hasAccess
    }

    func refreshAuthorization() async {
        if let pending = authorizationTask { await pending.value; return }
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        guard status != authorizationStatus else { return }
        permissionGeneration &+= 1
        let cleanup = Task {
            loadTask?.cancel(); changeTask?.cancel()
            loadTask = nil; changeTask = nil; loaded = false
            thumbnails.invalidate()
            try? await index.replace(with: [])
            await worker.clear()
            photoCount = 0; videoCount = 0; photosEarliestYear = nil
            authorizationStatus = status
            indexVersion &+= 1
            isLoading = false
            errorMessage = hasAccess ? nil : String.localized("permission.photo.library.description")
        }
        authorizationTask = cleanup
        await cleanup.value
        authorizationTask = nil
    }

    func loadPhotoIndex() async {
        if let pending = authorizationTask { await pending.value }
        if let task = loadTask { await task.value; return }
        if indexedTimeZone != TimeZone.current.identifier { loaded = false }
        guard hasAccess, !loaded else { return }
        let generation = permissionGeneration
        isLoading = true; errorMessage = nil
        let task = Task { [weak self] in
            guard let self else { return }
            defer { if generation == self.permissionGeneration { self.isLoading = false } }
            do {
                let update = try await self.worker.reload()
                try Task.checkCancellation()
                guard generation == self.permissionGeneration else { return }
                try await self.index.replace(with: update.upserts)
                try Task.checkCancellation()
                guard generation == self.permissionGeneration else { return }
                self.publish(update)
                self.loaded = true
                self.indexedTimeZone = TimeZone.current.identifier
            } catch is CancellationError {
            } catch {
                guard generation == self.permissionGeneration else { return }
                self.errorMessage = String.localized("error.library.retry")
            }
        }
        loadTask = task
        await task.value
        if generation == permissionGeneration { loadTask = nil }
    }

    nonisolated func photoLibraryDidChange(_ changeInstance: PHChange) {
        Task { @MainActor [weak self] in self?.enqueue(changeInstance) }
    }

    private func enqueue(_ change: PHChange) {
        guard hasAccess, authorizationTask == nil else { return }
        let previous = changeTask
        let initialLoad = loadTask
        let generation = permissionGeneration
        changeTask = Task { [weak self] in
            await previous?.value
            await initialLoad?.value
            guard let self, self.loaded, !Task.isCancelled, generation == self.permissionGeneration else { return }
            do {
                guard let update = try await self.worker.changes(change) else { return }
                try Task.checkCancellation()
                if update.replacesAll { try await self.index.replace(with: update.upserts) }
                else { await self.index.apply(upserting: update.upserts, removing: update.removals) }
                guard !Task.isCancelled, generation == self.permissionGeneration else { return }
                self.thumbnails.invalidate(ids: update.replacesAll ? nil : Set(update.removals + update.upserts.map(\.id)))
                self.publish(update)
            } catch is CancellationError {
            } catch {
                self.loaded = false
                self.errorMessage = String.localized("error.library.retry")
            }
        }
    }

    private func publish(_ update: LibraryIndexUpdate) {
        photoCount = update.photoCount; videoCount = update.videoCount
        photosEarliestYear = update.earliestYear
        indexVersion &+= 1; errorMessage = nil
    }

    func query(in region: MKCoordinateRegion, year: Int?, viewportSize: CGSize, mode: MapDisplayMode) async throws -> MapQueryResult {
        if let pending = authorizationTask { await pending.value }
        guard hasAccess else { return MapQueryResult(revision: indexVersion) }
        let generation = indexVersion
        let result = try await index.query(viewport: Self.viewport(region), width: viewportSize.width,
                                     height: viewportSize.height, year: year,
                                     cellSize: mode == .heatmap ? 24 : 64, limit: mode == .heatmap ? 1200 : 300)
        try Task.checkCancellation()
        if generation == indexVersion, mode == .photo { thumbnails.preheat(result.clusters.map(\.representativeID)) }
        return result
    }

    func initialRegion(year: Int?) async -> MKCoordinateRegion? {
        guard let bounds = try? await index.bounds(year: year) else { return nil }
        return MKCoordinateRegion(center: CLLocationCoordinate2D(latitude: bounds.latitude, longitude: bounds.longitude),
                                  span: MKCoordinateSpan(latitudeDelta: bounds.latitudeDelta, longitudeDelta: bounds.longitudeDelta))
    }

    func locations(in cluster: MapCluster, region: MKCoordinateRegion, year: Int?, offset: Int, limit: Int) async throws -> [PhotoLocation] {
        let members = try await index.members(of: cluster.cell, viewport: Self.viewport(region), year: year, offset: offset, limit: limit)
        try Task.checkCancellation()
        let assets = PHAsset.fetchAssets(withLocalIdentifiers: members.map(\.id), options: nil)
        var byID: [String: PHAsset] = [:]
        assets.enumerateObjects { asset, _, _ in byID[asset.localIdentifier] = asset }
        return members.compactMap { member in
            guard let asset = byID[member.id], let location = asset.location else { return nil }
            return PhotoLocation(id: member.id, coordinate: location.coordinate, asset: asset, creationDate: asset.creationDate)
        }
    }

    func location(for id: String) -> PhotoLocation? {
        guard hasAccess, let asset = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject,
              let location = asset.location else { return nil }
        return PhotoLocation(id: id, coordinate: location.coordinate, asset: asset, creationDate: asset.creationDate)
    }

    private static func viewport(_ region: MKCoordinateRegion) -> MapViewport {
        MapViewport(latitude: region.center.latitude, longitude: region.center.longitude,
                    latitudeDelta: region.span.latitudeDelta, longitudeDelta: region.span.longitudeDelta)
    }
}
