import Foundation
import AVFoundation
import CryptoKit

nonisolated enum LivePhotoMovie {
    private enum VerificationFailure: Error { case samplesChanged(AVMediaType); case readerIncomplete(Int) }
    static func contentIdentifier(_ url: URL) async throws -> String {
        let metadata = try await AVURLAsset(url: url).load(.metadata)
        guard let item = metadata.first(where: { $0.identifier?.rawValue.hasSuffix("com.apple.quicktime.content.identifier") == true }),
              let value = try await item.load(.stringValue), !value.isEmpty else { throw CardError.livePairing }
        return value
    }

    static func copy(from source: URL, to destination: URL, options: CardSaveOptions) async throws {
        if options.keepExif && options.keepLocation && options.keepCaptureTime {
            try FileManager.default.copyItem(at: source, to: destination)
            return
        }
        do {
            let asset = AVURLAsset(url: source)
            let tracks = try await asset.load(.tracks)
            var canPreserveTracks = true
            for track in tracks where track.mediaType == .metadata {
                let formats = try await track.load(.formatDescriptions)
                let keys = formats.flatMap { CMMetadataFormatDescriptionGetIdentifiers($0) as? [String] ?? [] }
                if keys.isEmpty || !keys.allSatisfy({ keep($0, options: options) }) { canPreserveTracks = false }
            }
            let types = Set(tracks.map(\.mediaType))
            guard types.isSubset(of: [.video, .audio, .metadata, AVMediaType(rawValue: "auxv")]) else {
                throw CardError.videoMetadata
            }
            if canPreserveTracks {
                // All timed keys are retained, so patch descriptive atoms without rewriting the track graph.
                try MovieMetadataCleaner.copy(from: source, to: destination, options: options, verifiedRenderingTracks: true)
                let outputTracks = try await AVURLAsset(url: destination).load(.tracks)
                guard tracks.map({ $0.mediaType.rawValue }).sorted() == outputTracks.map({ $0.mediaType.rawValue }).sorted() else {
                    throw CardError.videoMetadata
                }
            } else {
                guard types.isSubset(of: [.video, .audio, .metadata]) else { throw CardError.videoMetadata }
                let operation = try LivePhotoRemuxSession(source: source, destination: destination)
                try await operation.write(options: options)
            }
            for type in canPreserveTracks ? types : Set([AVMediaType.video, .audio]) {
                let original = try await sampleDigest(source, type: type)
                let output = try await sampleDigest(destination, type: type)
                guard original == output else { throw VerificationFailure.samplesChanged(type) }
            }
            if !options.keepCaptureTime { try MovieMetadataCleaner.clearContainerDates(at: destination) }
        } catch {
            try? FileManager.default.removeItem(at: destination)
            throw error
        }
    }

    static func keep(_ identifier: String, options: CardSaveOptions) -> Bool {
        let key = identifier.lowercased()
        if renderingKeys.contains(key.hasPrefix("mdta/") ? String(key.dropFirst(5)) : key) { return true }
        if ["location", "gps", "latitude", "longitude", "altitude", "©xyz", "loci"].contains(where: key.contains) { return options.keepLocation }
        if ["creationdate", "creation-date", "datetime", "©day"].contains(where: key.contains) { return options.keepCaptureTime }
        if ["make", "model", "software", "author", "copyright", "title", "camera.focal_length", "camera.lens_irisfnumber",
            "©mak", "©mod", "©swr", "©nam", "©art", "cprt"].contains(where: key.contains) { return options.keepExif }
        return false
    }

    private static let renderingKeys = Set([
        "content.identifier", "still-image-time", "video-orientation", "live-photo-info",
        "live-photo-still-image-transform", "live-photo-still-image-transform-reference-dimensions",
        "live-photo.auto", "full-frame-rate-playback-intent", "live-photo.vitality-score",
        "live-photo.vitality-scoring-version", "live-photo.subject-relighting-applied-curve-parameter",
        "smartstyle-info", "smartstyle.rendering-version", "smartstyle.tone", "smartstyle.color",
        "smartstyle.intensity", "smartstyle.bypassed", "smartstyle.cast",
        "texturestyle-info", "texturestyle.rendering-version", "texturestyle.preset",
        "texturestyle.intensity", "texturestyle.grain"
    ].map { "com.apple.quicktime." + $0 })

    static func sampleDigest(_ url: URL, type: AVMediaType) async throws -> String {
        let asset = AVURLAsset(url: url)
        var digest = SHA256()
        for track in try await asset.load(.tracks).filter({ $0.mediaType == type }) {
            let reader = try AVAssetReader(asset: asset)
            let output = AVAssetReaderTrackOutput(track: track, outputSettings: nil)
            let provider = reader.outputProvider(for: output)
            try reader.start()
            defer { if reader.status == .reading { reader.cancelReading() } }
            var samples = LivePhotoSampleDigest()
            while let sample = try await provider.next() {
                try Task.checkCancellation()
                // Keep Core Media buffers inside their scoped lifetime; avoid DynamicContent materialization.
                try sample.withUnsafeSampleBuffer { try samples.append($0) }
            }
            guard reader.status == .completed else { throw VerificationFailure.readerIncomplete(reader.status.rawValue) }
            samples.value.withUnsafeBytes { digest.update(bufferPointer: $0) }
        }
        return digest.finalize().description
    }
}
