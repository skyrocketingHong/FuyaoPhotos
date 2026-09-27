import Foundation
import AVFoundation

nonisolated enum LivePhotoMotionMovie {
    static func write(_ source: URL, to destination: URL) async throws -> Int64 {
        let asset = AVURLAsset(url: source)
        let composition = AVMutableComposition()
        let videoTracks = try await asset.loadTracks(withMediaType: .video)
        guard !videoTracks.isEmpty else { throw CardError.livePairing }
        let cover = try await coverTime(asset)
        for type in [AVMediaType.video, .audio] {
            for track in try await asset.loadTracks(withMediaType: type) {
                guard let target = composition.addMutableTrack(withMediaType: type, preferredTrackID: kCMPersistentTrackID_Invalid)
                else { throw CardError.videoMetadata }
                let range = try await track.load(.timeRange)
                try target.insertTimeRange(range, of: track, at: range.start)
                target.preferredTransform = try await track.load(.preferredTransform)
            }
        }
        guard let exporter = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetPassthrough)
        else { throw CardError.videoMetadata }
        exporter.metadata = try await asset.load(.metadata)
        do {
            try await exporter.export(to: destination, as: .mp4)
            for type in [AVMediaType.video, .audio] {
                let before = try await LivePhotoMovie.sampleDigest(source, type: type)
                let after = try await LivePhotoMovie.sampleDigest(destination, type: type)
                guard before == after else { throw CardError.videoMetadata }
            }
            return cover
        } catch { try? FileManager.default.removeItem(at: destination); throw error }
    }

    private static func coverTime(_ asset: AVAsset) async throws -> Int64 {
        for track in try await asset.loadTracks(withMediaType: .metadata) {
            let formats = try await track.load(.formatDescriptions)
            guard formats.contains(where: { format in
                guard let names = CMMetadataFormatDescriptionGetIdentifiers(format) as? [String], names.count == 1 else { return false }
                return names[0].hasSuffix("com.apple.quicktime.still-image-time")
            }) else { continue }
            let reader = try AVAssetReader(asset: asset)
            let output = AVAssetReaderTrackOutput(track: track, outputSettings: nil)
            let provider = reader.outputProvider(for: output)
            try reader.start()
            if let sample = try await provider.next(), sample.presentationTimeStamp.isNumeric {
                let time = CMTimeConvertScale(sample.presentationTimeStamp, timescale: 1_000_000, method: .default)
                reader.cancelReading()
                return max(0, time.value)
            }
        }
        // The standard uses -1 when a timed cover marker is unavailable.
        return -1
    }
}
