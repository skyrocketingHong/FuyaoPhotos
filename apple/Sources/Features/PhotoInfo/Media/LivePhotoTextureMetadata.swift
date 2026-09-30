import Foundation
import AVFoundation
import ImageIO

nonisolated enum LivePhotoTextureMetadata {
    static let identifier = "mdta/com.apple.quicktime.texturestyle-info"

    struct Frame: Sendable {
        let range: CMTimeRange
        let payload: Data
    }

    static func movieNeedsTextureTrack(_ url: URL) async throws -> Bool {
        let asset = AVURLAsset(url: url)
        return try await textureTrack(in: asset) == nil
    }

    static func requirePhotographicTrack(_ url: URL) async throws {
        guard try await metadataTrack(in: AVURLAsset(url: url), identifier: "mdta/com.apple.quicktime.smartstyle-info") != nil else {
            throw CardError.liveStyleMetadata
        }
    }

    static func addIfNeeded(source: URL, photo: URL, destination: URL) async throws {
        let asset = AVURLAsset(url: source)
        if try await textureTrack(in: asset) != nil {
            try FileManager.default.copyItem(at: source, to: destination)
            return
        }
        let tracks = try await asset.load(.tracks)
        guard let video = tracks.first(where: { $0.mediaType == .video }), video.trackID > 0,
              let smart = try await metadataTrack(in: asset, identifier: "mdta/com.apple.quicktime.smartstyle-info") else {
            throw CardError.liveStyleMetadata
        }
        let image = try HeifContainer.load(fileURL: photo)
        guard let item = image.items.first(where: { image.contentType(of: $0) == AppleTextureStyles.textureStylesContentType }),
              let texture = try PropertyListSerialization.propertyList(from: Data(image.payload(of: item.id)), options: [], format: nil) as? [String: Any],
              texture["Preset"] as? String == "Standard",
              let reader = CGImageSourceCreateWithURL(photo as CFURL, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(reader, 0, nil) as? [String: Any],
              let exif = properties[kCGImagePropertyExifDictionary as String] as? [String: Any],
              let brightness = exif[kCGImagePropertyExifBrightnessValue as String] as? NSNumber,
              brightness.doubleValue.isFinite else { throw CardError.liveStyleMetadata }
        // This is a neutral processing profile, not a replacement for the source camera's EXIF.
        let payload = try PropertyListSerialization.data(fromPropertyList: [
            "Preset": "Standard", "CaptureMode": "LivePhoto", "CaptureType": "None",
            "HardwareModel": texture["HardwareModel"] as? String ?? "iPhone 18 Pro",
            "PortType": texture["PortType"] as? String ?? "PortTypeBack",
            "TextureStylePeopleDataVersion": 3,
            "TextureStyleFaceAttitudeMetadata": [Any](),
            "BrightnessValue": brightness.doubleValue
        ], format: .binary, options: 0)
        let frames = try await frames(of: smart, asset: asset, payload: payload)
        guard !frames.isEmpty else { throw CardError.liveStyleMetadata }
        let scratch = FileManager.default.temporaryDirectory.appendingPathComponent("FuyaoTextureTrack-\(UUID().uuidString).mov")
        defer { try? FileManager.default.removeItem(at: scratch) }
        let duration = try await asset.load(.duration)
        try await writeTrack(frames, duration: duration, timeScale: MovieTrackAppender.timeScale(of: source), to: scratch)
        try MovieTrackAppender.append(metadataMovie: scratch, to: source, destination: destination,
            describedVideoID: UInt32(video.trackID))
        let output = AVURLAsset(url: destination)
        guard let added = try await textureTrack(in: output) else { throw CardError.liveStyleMetadata }
        let verified = try await Self.frames(of: added, asset: output)
        guard verified.count == frames.count, zip(verified, frames).allSatisfy({
            CMTimeCompare($0.range.start, $1.range.start) == 0 && CMTimeCompare($0.range.duration, $1.range.duration) == 0
                && $0.payload == $1.payload
        }) else { throw CardError.liveStyleMetadata }
        for type in Set(tracks.map(\.mediaType)) where type != .metadata {
            guard try await LivePhotoMovie.sampleDigest(source, type: type) == LivePhotoMovie.sampleDigest(destination, type: type) else {
                throw CardError.liveStyleMetadata
            }
        }
    }

    private static func textureTrack(in asset: AVAsset) async throws -> AVAssetTrack? {
        try await metadataTrack(in: asset, identifier: identifier)
    }

    private static func metadataTrack(in asset: AVAsset, identifier: String) async throws -> AVAssetTrack? {
        for track in try await asset.load(.tracks) where track.mediaType == .metadata {
            let descriptions = try await track.load(.formatDescriptions)
            if descriptions.contains(where: { (CMMetadataFormatDescriptionGetIdentifiers($0) as? [String] ?? []).contains(identifier) }) { return track }
        }
        return nil
    }

    private static func frames(of track: AVAssetTrack, asset: AVAsset, payload: Data? = nil) async throws -> [Frame] {
        let reader = try AVAssetReader(asset: asset)
        let provider = reader.outputMetadataProvider(for: AVAssetReaderTrackOutput(track: track, outputSettings: nil))
        try reader.start()
        defer { if reader.status == .reading { reader.cancelReading() } }
        var result: [Frame] = []
        while let group = try await provider.next() {
            try Task.checkCancellation()
            guard (result.count < 18_000), group.timeRange.isValid, group.timeRange.start.isNumeric,
                  group.timeRange.duration.isNumeric, CMTimeCompare(group.timeRange.duration, .zero) > 0 else { throw CardError.liveStyleMetadata }
            let data: Data
            if let payload { data = payload }
            else {
                guard let item = group.items.first(where: { $0.identifier?.rawValue == identifier }),
                      let value = try await item.load(.dataValue) else { throw CardError.liveStyleMetadata }
                data = value
            }
            result.append(Frame(range: group.timeRange, payload: data))
        }
        guard reader.status == .completed else { throw CardError.liveStyleMetadata }
        return result
    }

    private static func writeTrack(_ frames: [Frame], duration: CMTime, timeScale: Int32, to url: URL) async throws {
        let writer = try AVAssetWriter(outputURL: url, fileType: .mov)
        writer.movieTimeScale = timeScale
        writer.metadata = [("rendering-version", 1.0, true), ("preset", 1.0, true),
                           ("intensity", 1.0, false), ("grain", 0.0, false)].map { suffix, value, integer in
            let item = AVMutableMetadataItem()
            item.identifier = AVMetadataIdentifier(rawValue: "mdta/com.apple.quicktime.texturestyle." + suffix)
            item.dataType = (integer ? kCMMetadataBaseDataType_SInt32 : kCMMetadataBaseDataType_Float64) as String
            item.value = integer ? NSNumber(value: Int32(value)) : NSNumber(value: value)
            return item
        }
        let specification = [[
            kCMMetadataFormatDescriptionMetadataSpecificationKey_Identifier as String: identifier,
            kCMMetadataFormatDescriptionMetadataSpecificationKey_DataType as String: kCMMetadataBaseDataType_RawData as String
        ]]
        var description: CMMetadataFormatDescription?
        guard CMMetadataFormatDescriptionCreateWithMetadataSpecifications(allocator: kCFAllocatorDefault,
            metadataType: kCMMetadataFormatType_Boxed, metadataSpecifications: specification as CFArray,
            formatDescriptionOut: &description) == noErr, let description else { throw CardError.liveStyleMetadata }
        let input = AVAssetWriterInput(mediaType: .metadata, outputSettings: nil, sourceFormatHint: description)
        guard writer.canAdd(input) else { throw CardError.liveStyleMetadata }
        let receiver = writer.inputMetadataReceiver(for: input)
        try writer.start()
        writer.startSession(atSourceTime: .zero)
        do {
            for frame in frames {
                try Task.checkCancellation()
                let item = AVMutableMetadataItem()
                item.identifier = AVMetadataIdentifier(rawValue: identifier)
                item.dataType = kCMMetadataBaseDataType_RawData as String
                item.value = frame.payload as NSData
                try await receiver.append(AVTimedMetadataGroup(items: [item], timeRange: frame.range))
            }
            receiver.finish()
            writer.endSession(atSourceTime: duration)
            await writer.finishWriting()
            guard writer.status == .completed else { throw CardError.liveStyleMetadata }
        } catch {
            writer.cancelWriting()
            throw error
        }
    }
}
