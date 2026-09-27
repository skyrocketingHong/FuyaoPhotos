import Foundation
@preconcurrency import Photos
import UniformTypeIdentifiers
import ImageIO

/// Library writes for metadata-only changes: the injected HEIC becomes a new asset, or
/// replaces the rendered content of the original through content-editing output. Live
/// Photo pairing rides along on save-as-new because the injected photo keeps tag 17.
@MainActor enum MetadataPhotoLibrary {
    static func save(photo: URL, document: CardDocument, updateOriginal: Bool, textureStyles: Bool,
                     options: CardSaveOptions = CardSaveOptions(keepLocation: true)) async throws {
        guard let imageSource = CGImageSourceCreateWithURL(photo as CFURL, nil),
              let identifier = CGImageSourceGetType(imageSource), let type = UTType(identifier as String) else { throw CardError.invalidImage }
        let access: PHAccessLevel = updateOriginal ? .readWrite : .addOnly
        let status = await PHPhotoLibrary.requestAuthorization(for: access)
        guard status == .authorized || status == .limited else { throw CardError.permission }
        if updateOriginal {
            guard let identifier = document.assetIdentifier, !document.isLive else { throw CardError.unavailable }
            guard let source = CardPhotoLibrary.asset(identifier) else { throw CardError.permission }
            let input = try await CardPhotoLibrary.input(for: source)
            let output = PHContentEditingOutput(contentEditingInput: input)
            guard output.supportedRenderedContentTypes.contains(type) else { throw CardError.exportFailed }
            let target = try output.renderedContentURL(for: type)
            // PhotoKit may reserve the rendered URL with an empty file.
            try Data(contentsOf: photo).write(to: target, options: .atomic)
            output.adjustmentData = PHAdjustmentData(formatIdentifier: "ing.fuyaoskyrocket.photos.metadata",
                                                     formatVersion: "1",
                                                     data: try JSONEncoder().encode(["texture": textureStyles]))
            try await PHPhotoLibrary.shared().performChanges {
                let request = PHAssetChangeRequest(for: source)
                request.contentEditingOutput = output
                if !options.keepLocation { request.location = nil }
                if !options.keepCaptureTime { request.creationDate = Date() }
            }
        } else {
            var movie: URL?
            defer { if let movie { try? FileManager.default.removeItem(at: movie) } }
            if let original = document.sourceMovieURL {
                let target = photo.deletingPathExtension().appendingPathExtension("mov")
                try await LivePhotoMovie.copy(from: original, to: target, options: options)
                movie = target
            }
            if let movie { try await LivePhotoPair.validate(photo: photo, movie: movie) }
            let readStatus = PHPhotoLibrary.authorizationStatus(for: .readWrite)
            let source = readStatus == .authorized || readStatus == .limited
                ? CardPhotoLibrary.asset(document.assetIdentifier) : nil
            // ASVS 5.3.2: sanitize the source name before using it as PhotoKit metadata.
            let resourceName = resourceFilename(for: document.originalName, extension: type.preferredFilenameExtension ?? photo.pathExtension)
            try await PHPhotoLibrary.shared().performChanges {
                let request = PHAssetCreationRequest.forAsset()
                let imageOptions = PHAssetResourceCreationOptions()
                imageOptions.originalFilename = resourceName
                imageOptions.contentType = type
                request.addResource(with: .photo, fileURL: photo, options: imageOptions)
                if let movie {
                    let videoOptions = PHAssetResourceCreationOptions()
                    videoOptions.originalFilename = movie.lastPathComponent
                    videoOptions.contentType = .quickTimeMovie
                    request.addResource(with: .pairedVideo, fileURL: movie, options: videoOptions)
                }
                request.location = options.keepLocation ? source?.location ?? document.location : nil
                if options.keepCaptureTime, let date = source?.creationDate { request.creationDate = date }
                else if !options.keepCaptureTime { request.creationDate = Date() }
            }
        }
    }

    private static func resourceFilename(for originalName: String, extension fileExtension: String) -> String {
        let basename = (originalName as NSString).lastPathComponent
        let stem = (basename as NSString).deletingPathExtension
        let safe = String(stem.unicodeScalars
            .filter { !CharacterSet.controlCharacters.contains($0) && $0 != "/" && $0 != "\\" }
            .map { String($0) }
            .joined()
            .prefix(80))
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return "\(safe.isEmpty ? "FuyaoPhoto" : safe).\(fileExtension)"
    }
}
