import Foundation
@preconcurrency import Photos
import PhotoMapCore

nonisolated struct LibraryIndexUpdate: Sendable {
    let upserts: [PhotoCoordinate]
    let removals: [String]
    let replacesAll: Bool
    let photoCount: Int
    let videoCount: Int
    let earliestYear: Int?
}

/// PhotoKit results are immutable snapshots; only this actor advances the current result.
actor PhotoLibraryIndexWorker {
    private var fetchResult: PHFetchResult<PHAsset>?
    private var cached: [String: CachedPhoto] = [:]
    private var loadedCache = false
    private var persistenceTask: Task<Void, Never>?
    private var yearCounts: [Int: Int] = [:]
    private var calendar: Calendar { Calendar.current }
    private var cacheTimeZone: String?
    private struct CachedPhoto: Codable {
        let id: String
        let modified: Date?
        let year: Int?
        let coordinate: PhotoCoordinate?
    }
    private struct Snapshot: Codable {
        let version: Int
        let timezone: String
        let photos: [CachedPhoto]
    }
    private var cacheURL: URL? {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?
            .appendingPathComponent(Bundle.main.bundleIdentifier ?? "PhotoMap", isDirectory: true)
            .appendingPathComponent("coordinate-index-v1.json")
    }

    func reload() throws -> LibraryIndexUpdate {
        try Task.checkCancellation()
        if let cacheTimeZone, cacheTimeZone != calendar.timeZone.identifier { cached.removeAll() }
        if !loadedCache, let url = cacheURL, let data = try? Data(contentsOf: url),
           let snapshot = try? JSONDecoder().decode(Snapshot.self, from: data),
           snapshot.version == 1, snapshot.timezone == calendar.timeZone.identifier {
            cached = Dictionary(snapshot.photos.map { ($0.id, $0) }, uniquingKeysWith: { _, new in new })
        }
        loadedCache = true
        cacheTimeZone = calendar.timeZone.identifier
        let result = PHAsset.fetchAssets(with: .image, options: nil)
        var next: [String: CachedPhoto] = [:]
        var coordinates: [PhotoCoordinate] = []
        coordinates.reserveCapacity(result.count)
        for i in 0..<result.count {
            if i.isMultiple(of: 256) { try Task.checkCancellation() }
            let asset = result.object(at: i)
            let entry: CachedPhoto
            if let previous = cached[asset.localIdentifier], let modified = asset.modificationDate,
               modified == previous.modified { entry = previous }
            else { entry = metadata(asset) }
            next[entry.id] = entry
            if let coordinate = entry.coordinate { coordinates.append(coordinate) }
        }
        try Task.checkCancellation()
        fetchResult = result
        cached = next // Reconcile access and deletions before publishing the cached coordinates.
        yearCounts = [:]
        for photo in next.values { if let year = photo.year { yearCounts[year, default: 0] += 1 } }
        schedulePersistence()
        return update(upserts: coordinates, removals: [], replacesAll: true)
    }

    func changes(_ change: PHChange) throws -> LibraryIndexUpdate? {
        try Task.checkCancellation()
        guard let before = fetchResult, let details = change.changeDetails(for: before) else { return nil }
        guard details.hasIncrementalChanges else { return try reload() }
        var removals = details.removedObjects.map(\.localIdentifier)
        var upserts: [PhotoCoordinate] = []
        for id in removals { removeCached(id) }
        for asset in details.insertedObjects + details.changedObjects {
            let entry = metadata(asset)
            removeCached(entry.id)
            cached[entry.id] = entry
            if let year = entry.year { yearCounts[year, default: 0] += 1 }
            removals.append(entry.id) // Includes changed assets that lost their location.
            if let coordinate = entry.coordinate { upserts.append(coordinate) }
        }
        fetchResult = details.fetchResultAfterChanges
        schedulePersistence()
        return update(upserts: upserts, removals: removals, replacesAll: false)
    }

    func clear() {
        persistenceTask?.cancel(); persistenceTask = nil; yearCounts = [:]
        fetchResult = nil; cached.removeAll(); loadedCache = true
        if let url = cacheURL { try? FileManager.default.removeItem(at: url) }
    }

    private func metadata(_ asset: PHAsset) -> CachedPhoto {
        let date = asset.creationDate
        let year = date.map { calendar.component(.year, from: $0) }
        let coordinate = asset.location.map {
            PhotoCoordinate(id: asset.localIdentifier, latitude: $0.coordinate.latitude,
                            longitude: $0.coordinate.longitude, creationDate: date, year: year)
        }.flatMap { $0.isValid ? $0 : nil }
        return CachedPhoto(id: asset.localIdentifier, modified: asset.modificationDate, year: year, coordinate: coordinate)
    }

    private func update(upserts: [PhotoCoordinate], removals: [String], replacesAll: Bool) -> LibraryIndexUpdate {
        LibraryIndexUpdate(upserts: upserts, removals: removals, replacesAll: replacesAll,
                           photoCount: fetchResult?.count ?? 0,
                           videoCount: PHAsset.fetchAssets(with: .video, options: nil).count,
                           earliestYear: yearCounts.keys.min())
    }

    private func removeCached(_ id: String) {
        if let previous = cached.removeValue(forKey: id), let year = previous.year {
            let remaining = (yearCounts[year] ?? 1) - 1
            if remaining <= 0 { yearCounts.removeValue(forKey: year) }
            else { yearCounts[year] = remaining }
        }
    }

    private func schedulePersistence() {
        persistenceTask?.cancel()
        persistenceTask = Task { [weak self] in
            do { try await Task.sleep(for: .seconds(1)); try Task.checkCancellation() }
            catch { return }
            await self?.persist()
        }
    }

    private func persist() {
        guard let url = cacheURL else { return }
        do {
            let snapshot = Snapshot(version: 1, timezone: calendar.timeZone.identifier, photos: Array(cached.values))
            try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try JSONEncoder().encode(snapshot).write(to: url, options: .atomic)
            try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: url.path)
        } catch { /* Disposable cache failure does not prevent access to the Photos source of truth. */ }
    }
}
