import Foundation
@preconcurrency import Photos
import PhotoMapCore
#if canImport(UIKit)
import UIKit
#else
import AppKit
#endif

/// A shared, bounded request scheduler. Each consumer cancels only its own waiter.
@MainActor final class PhotoThumbnailStore {
    private struct Key: Hashable { let id: String; let size: Int }
    private final class Request {
        var waiters: [UUID: CheckedContinuation<NativeImage?, any Error>] = [:]
        var requestID: PHImageRequestID?
        let token = UUID()
    }
    private let manager = PHCachingImageManager()
    private var cache = CostLimitedCache<Key, NativeImage>(countLimit: 256, costLimit: 32 * 1024 * 1024)
    private var requests: [Key: Request] = [:]
    private var queue: [Key] = []
    private var active = 0
    private var suspended = false
    private var memoryObserver: NSObjectProtocol?
    private var preheatedIDs: [String] = []

    init() {
        #if canImport(UIKit)
        memoryObserver = NotificationCenter.default.addObserver(forName: UIApplication.didReceiveMemoryWarningNotification,
                                                                object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.cache.removeAll()
                self?.manager.stopCachingImagesForAllAssets()
            }
        }
        #endif
    }
    deinit { if let memoryObserver { NotificationCenter.default.removeObserver(memoryObserver) } }

    func image(for assetID: String, pixelSize: Int = 108) async throws -> NativeImage? {
        try Task.checkCancellation()
        let key = Key(id: assetID, size: min(2048, max(32, pixelSize)))
        if let image = cache.value(for: key) { return image }
        let waiter = UUID()
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                guard !Task.isCancelled else { continuation.resume(throwing: CancellationError()); return }
                if let existing = requests[key] { existing.waiters[waiter] = continuation }
                else {
                    let request = Request(); request.waiters[waiter] = continuation
                    requests[key] = request; queue.append(key)
                }
                drain()
            }
        } onCancel: {
            Task { @MainActor [weak self] in self?.cancel(key: key, waiter: waiter) }
        }
    }

    func cancelAll() {
        suspended = true
        for (key, request) in Array(requests) { finish(key: key, token: request.token, result: .failure(CancellationError())) }
        queue.removeAll()
        manager.stopCachingImagesForAllAssets()
        preheatedIDs = []
        suspended = false
    }

    /// Warm a small set of visible representatives, never the complete photo library.
    func preheat(_ ids: [String]) {
        let next = Array(ids.prefix(12))
        guard next != preheatedIDs else { return }
        manager.stopCachingImagesForAllAssets()
        preheatedIDs = next
        let assets = PHAsset.fetchAssets(withLocalIdentifiers: next, options: nil)
        var items: [PHAsset] = []
        assets.enumerateObjects { asset, _, _ in items.append(asset) }
        let options = PHImageRequestOptions()
        options.deliveryMode = .fastFormat
        options.resizeMode = .fast
        options.isNetworkAccessAllowed = false
        manager.startCachingImages(for: items, targetSize: CGSize(width: 108, height: 108), contentMode: .aspectFit, options: options)
    }

    func invalidate(ids: Set<String>? = nil) {
        if let ids {
            suspended = true
            cache.remove(where: { ids.contains($0.id) })
            for (key, request) in Array(requests) where ids.contains(key.id) {
                finish(key: key, token: request.token, result: .failure(CancellationError()))
            }
            suspended = false; drain()
        } else { cancelAll(); cache.removeAll() }
    }

    private func drain() {
        guard !suspended else { return }
        while active < 5 && !queue.isEmpty {
            let key = queue.removeFirst()
            guard let request = requests[key], request.requestID == nil else { continue }
            guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [key.id], options: nil).firstObject else {
                requests.removeValue(forKey: key)
                for waiter in request.waiters.values { waiter.resume(returning: nil) }
                continue
            }
            active += 1
            let options = PHImageRequestOptions()
            options.deliveryMode = key.size > 256 ? .highQualityFormat : .fastFormat
            options.resizeMode = .fast; options.isSynchronous = false; options.isNetworkAccessAllowed = true
            let token = request.token
            request.requestID = manager.requestImage(for: asset, targetSize: CGSize(width: key.size, height: key.size),
                                                     contentMode: .aspectFit, options: options) { [weak self] image, info in
                let cancelled = (info?[PHImageCancelledKey] as? Bool) == true
                let failed = info?[PHImageErrorKey] != nil
                // fastFormat can deliver only a degraded image, which is still a valid thumbnail.
                Task { @MainActor in
                    guard let self else { return }
                    if cancelled { self.finish(key: key, token: token, result: .failure(CancellationError())) }
                    else { self.finish(key: key, token: token, result: .success(failed ? nil : image)) }
                }
            }
        }
    }

    private func cancel(key: Key, waiter: UUID) {
        guard let request = requests[key], let continuation = request.waiters.removeValue(forKey: waiter) else { return }
        continuation.resume(throwing: CancellationError())
        if request.waiters.isEmpty { finish(key: key, token: request.token, result: .failure(CancellationError())) }
    }

    private func finish(key: Key, token: UUID, result: Result<NativeImage?, any Error>) {
        guard let request = requests[key], request.token == token else { return }
        requests.removeValue(forKey: key)
        queue.removeAll { $0 == key }
        if let id = request.requestID { manager.cancelImageRequest(id); active -= 1 }
        if case let .success(image?) = result {
            #if os(macOS)
            let decoded = image.cgImage(forProposedRect: nil, context: nil, hints: nil)
            #else
            let decoded = image.cgImage
            #endif
            let cost = decoded.map { $0.bytesPerRow * $0.height } ?? key.size * key.size * 4
            cache.insert(image, for: key, cost: cost)
        }
        for waiter in request.waiters.values { waiter.resume(with: result) }
        drain()
    }
}
