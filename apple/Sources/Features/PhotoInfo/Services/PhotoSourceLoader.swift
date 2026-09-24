import Foundation
@preconcurrency import Photos
@preconcurrency import PhotosUI
import UniformTypeIdentifiers

@MainActor enum PhotoSourceLoader {
    static func load(_ result: PHPickerResult, into folder: URL) async throws -> PhotoSourceResources {
        if let asset = CardPhotoLibrary.asset(result.assetIdentifier) {
            return try await load(asset, into: folder)
        }
        let provider = result.itemProvider
#if os(macOS)
        if provider.hasItemConformingToTypeIdentifier(UTType.livePhoto.identifier) {
            _ = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
            guard let asset = CardPhotoLibrary.asset(result.assetIdentifier) else { throw CardError.permission }
            return try await load(asset, into: folder)
        }
#else
        if provider.canLoadObject(ofClass: PHLivePhoto.self) {
            let live = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<PHLivePhoto, Error>) in
                provider.loadObject(ofClass: PHLivePhoto.self) { object, error in
                    if let live = object as? PHLivePhoto { continuation.resume(returning: live) }
                    else { continuation.resume(throwing: error ?? CardError.livePairing) }
                }
            }
            let resources = try await copy(PHAssetResource.assetResources(for: live), into: folder, assetID: result.assetIdentifier, live: true)
            withExtendedLifetime(live) {} // The picker owns temporary resources until both copies finish.
            return resources
        }
#endif
        guard let type = provider.registeredTypeIdentifiers.first(where: { UTType($0)?.conforms(to: .image) == true }) else { throw CardError.invalidImage }
        let url = folder.appendingPathComponent("original.\(UTType(type)?.preferredFilenameExtension ?? "image")")
        let name: String = try await withCheckedThrowingContinuation { continuation in
            provider.loadFileRepresentation(forTypeIdentifier: type) { source, error in
                do {
                    guard let source else { throw error ?? CardError.unavailable }
                    let bytes = (try source.resourceValues(forKeys: [.fileSizeKey])).fileSize ?? 0
                    guard bytes > 0, bytes <= 512 * 1024 * 1024 else { throw CardError.tooLarge }
                    try FileManager.default.copyItem(at: source, to: url)
                    continuation.resume(returning: source.lastPathComponent)
                } catch { continuation.resume(throwing: error) }
            }
        }
        return PhotoSourceResources(image: url, movie: nil, originalName: name, assetIdentifier: result.assetIdentifier)
    }

    static func load(_ asset: PHAsset, into folder: URL) async throws -> PhotoSourceResources {
        try await copy(PHAssetResource.assetResources(for: asset), into: folder,
                       assetID: asset.localIdentifier, live: asset.mediaSubtypes.contains(.photoLive))
    }

    private static func copy(_ resources: [PHAssetResource], into folder: URL, assetID: String?, live: Bool) async throws -> PhotoSourceResources {
        // Use a matched current pair, or a matched original pair; never mix generations.
        let currentPhoto = resources.first { $0.type == .fullSizePhoto }
        let currentMovie = resources.first { $0.type == .fullSizePairedVideo }
        let useCurrent = currentPhoto != nil && (!live || currentMovie != nil)
        guard let photo = useCurrent ? currentPhoto : resources.first(where: { $0.type == .photo }) else { throw CardError.unavailable }
        let video = live ? (useCurrent ? currentMovie : resources.first(where: { $0.type == .pairedVideo })) : nil
        if live && video == nil { throw CardError.livePairing }
        let imageURL = folder.appendingPathComponent("original.\(photo.contentType.preferredFilenameExtension ?? "heic")")
        try await write(photo, to: imageURL)
        var movieURL: URL?
        if let video {
            let destination = folder.appendingPathComponent("original.mov")
            try await write(video, to: destination)
            movieURL = destination
        }
        let name: String
        if #available(iOS 27, macOS 27, *) { name = photo.filename ?? imageURL.lastPathComponent }
        else { name = photo.originalFilename }
        return PhotoSourceResources(image: imageURL, movie: movieURL, originalName: name, assetIdentifier: assetID)
    }

    private static func write(_ resource: PHAssetResource, to url: URL) async throws {
        try Task.checkCancellation()
        let options = PHAssetResourceRequestOptions()
        options.isNetworkAccessAllowed = true
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHAssetResourceManager.default().writeData(for: resource, toFile: url, options: options) { error in
                if let error { continuation.resume(throwing: error) } else { continuation.resume() }
            }
        }
        try Task.checkCancellation()
    }
}
