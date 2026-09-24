import Foundation
import AVFoundation

/// AVFoundation's non-Sendable reader/writer stay on one actor; each returned channel has its own owner.
actor LivePhotoRemuxSession {
    private enum Failure: Error { case completion(Int, Int) }
    private let asset: AVURLAsset
    private let reader: AVAssetReader
    private let writer: AVAssetWriter

    init(source: URL, destination: URL) throws {
        asset = AVURLAsset(url: source)
        reader = try AVAssetReader(asset: asset)
        writer = try AVAssetWriter(outputURL: destination, fileType: .mov)
    }

    func write(options: CardSaveOptions) async throws {
        var streams: [LivePhotoStream] = []
        var hasVideo = false
        writer.metadata = try await asset.load(.metadata).filter { LivePhotoMovie.keep($0.identifier?.rawValue ?? "", options: options) }
        for track in try await asset.load(.tracks) {
            let type = track.mediaType
            guard type == .video || type == .audio || type == .metadata else { continue }
            guard let format = try await track.load(.formatDescriptions).first else { throw CardError.videoMetadata }
            if type == .metadata {
                let identifiers = CMMetadataFormatDescriptionGetIdentifiers(format) as? [String] ?? []
                if !identifiers.contains(where: { LivePhotoMovie.keep($0, options: options) }) { continue }
            }
            let output = AVAssetReaderTrackOutput(track: track, outputSettings: nil)
            let input = AVAssetWriterInput(mediaType: type, outputSettings: nil, sourceFormatHint: format)
            if type == .video { hasVideo = true; input.transform = try await track.load(.preferredTransform) }
            guard reader.canAdd(output), writer.canAdd(input) else { throw CardError.videoMetadata }
            if type == .metadata {
                streams.append(LivePhotoStream(.metadata(reader.outputMetadataProvider(for: output), writer.inputMetadataReceiver(for: input))))
            } else {
                streams.append(LivePhotoStream(.samples(reader.outputProvider(for: output), writer.inputReceiver(for: input))))
            }
        }
        guard hasVideo else { throw CardError.videoMetadata }
        do {
            try writer.start()
            writer.startSession(atSourceTime: .zero)
            try reader.start()
            try await withThrowingTaskGroup(of: Void.self) { group in
                for stream in streams {
                    group.addTask {
                        do { try await stream.copy(options: options) }
                        catch { await self.cancel(); throw error }
                    }
                }
                try await group.waitForAll()
            }
            await writer.finishWriting()
            guard writer.status == .completed, reader.status == .completed else { throw Failure.completion(writer.status.rawValue, reader.status.rawValue) }
        } catch {
            cancel()
            throw error
        }
    }

    private func cancel() {
        if reader.status == .reading { reader.cancelReading() }
        if writer.status == .writing { writer.cancelWriting() }
    }
}
