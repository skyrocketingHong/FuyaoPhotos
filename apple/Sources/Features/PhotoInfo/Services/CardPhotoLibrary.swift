import Foundation
@preconcurrency import Photos
import UniformTypeIdentifiers
import CoreLocation

@MainActor enum CardPhotoLibrary {
    static func asset(_ identifier: String?) -> PHAsset? {
        guard let identifier else { return nil }
        return PHAsset.fetchAssets(withLocalIdentifiers: [identifier], options: nil).firstObject
    }

    static func input(for asset: PHAsset) async throws -> PHContentEditingInput {
        let options = PHContentEditingInputRequestOptions()
        options.isNetworkAccessAllowed = true
        options.canHandleAdjustmentData = { _ in false }
        return try await withCheckedThrowingContinuation { continuation in
            asset.requestContentEditingInput(with: options) { input, _ in
                if let input { continuation.resume(returning: input) }
                else { continuation.resume(throwing: CardError.unavailable) }
            }
        }
    }

    static func pairedVideo(for asset: PHAsset, to url: URL) async throws {
        let resources = PHAssetResource.assetResources(for: asset)
        guard let resource = resources.first(where: { $0.type == .fullSizePairedVideo }) ?? resources.first(where: { $0.type == .pairedVideo }) else { throw CardError.unavailable }
        let options = PHAssetResourceRequestOptions()
        options.isNetworkAccessAllowed = true
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHAssetResourceManager.default().writeData(for: resource, toFile: url, options: options) { error in
                if let error { continuation.resume(throwing: error) } else { continuation.resume() }
            }
        }
    }

    static func save(photo: URL, movie: URL?, document: CardDocument, options: CardSaveOptions) async throws {
        let access: PHAccessLevel = options.updateOriginal ? .readWrite : .addOnly
        let status = await PHPhotoLibrary.requestAuthorization(for: access)
        guard status == .authorized || status == .limited else { throw CardError.permission }
        let readStatus = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        let source = readStatus == .authorized || readStatus == .limited ? asset(document.assetIdentifier) : nil
        if options.updateOriginal {
            guard let source, !document.isLive else { throw CardError.unavailable }
            let input = try await input(for: source)
            let output = PHContentEditingOutput(contentEditingInput: input)
            let type: UTType = options.format == .heic ? .heic : options.format == .png ? .png : .jpeg
            guard output.supportedRenderedContentTypes.contains(type) else { throw CardError.exportFailed }
            let target = try output.renderedContentURL(for: type)
            // PhotoKit may reserve the rendered URL with an empty file.
            try Data(contentsOf: photo).write(to: target, options: .atomic)
            output.adjustmentData = PHAdjustmentData(formatIdentifier: "ing.fuyaoskyrocket.photos.card", formatVersion: "1", data: try JSONEncoder().encode(document.card))
            try await PHPhotoLibrary.shared().performChanges {
                let request = PHAssetChangeRequest(for: source)
                request.contentEditingOutput = output
                if !options.keepLocation { request.location = nil }
                if !options.keepCaptureTime { request.creationDate = Date() }
            }
        } else {
            if let movie { try await LivePhotoPair.validate(photo: photo, movie: movie) }
            let location: CLLocation? = options.keepLocation ? document.location : nil
            let date: Date? = options.keepCaptureTime ? source?.creationDate : nil
            try await PHPhotoLibrary.shared().performChanges {
                let request = PHAssetCreationRequest.forAsset()
                let imageOptions = PHAssetResourceCreationOptions()
                imageOptions.originalFilename = photo.lastPathComponent
                imageOptions.contentType = options.format == .heic ? .heic : options.format == .png ? .png : .jpeg
                request.addResource(with: .photo, fileURL: photo, options: imageOptions)
                if let movie {
                    let videoOptions = PHAssetResourceCreationOptions()
                    videoOptions.originalFilename = movie.lastPathComponent
                    videoOptions.contentType = .quickTimeMovie
                    request.addResource(with: .pairedVideo, fileURL: movie, options: videoOptions)
                }
                request.location = location
                if let date { request.creationDate = date }
                else if !options.keepCaptureTime { request.creationDate = Date() }
            }
        }
    }

}
