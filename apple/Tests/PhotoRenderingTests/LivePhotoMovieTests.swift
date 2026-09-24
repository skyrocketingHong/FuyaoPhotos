import Testing
import Foundation
import AVFoundation
import CoreVideo
import CoreImage
import AudioToolbox
import CryptoKit
@testable import PhotoRenderingCore

struct LivePhotoMovieTests {
    @Test func modernRemuxSampleTimingMatches() async throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let input = folder.appendingPathComponent("in.mov"), output = folder.appendingPathComponent("out.mov")
        try await Self.fixture(at: input)
        try await LivePhotoRemuxSession(source: input, destination: output).write(options: CardSaveOptions())
        func signatures(_ url: URL) async throws -> [String] {
            let asset = AVURLAsset(url: url)
            let track = try #require(try await asset.loadTracks(withMediaType: .video).first)
            let reader = try AVAssetReader(asset: asset)
            let provider = reader.outputProvider(for: AVAssetReaderTrackOutput(track: track, outputSettings: nil))
            try reader.start()
            var result: [String] = []
            while let sample = try await provider.next() {
                if let data = CMReadySampleBuffer<CMReadOnlyDataBlockBuffer>(sample)?.content {
                    let bytes = Data(data)
                    result.append("\(CMTimeGetSeconds(sample.presentationTimeStamp)) size \(bytes.count) prefix \(bytes.prefix(16).map { String(format: "%02x", $0) }.joined()) / \(SHA256.hash(data: bytes))")
                }
            }
            return result
        }
        let original = try await signatures(input)
        let rendered = try await signatures(output)
        #expect(original == rendered)
    }

    @Test func remuxPreservesVideoAndLiveMarkerWhileRemovingLocation() async throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let input = folder.appendingPathComponent("input.mov"), output = folder.appendingPathComponent("output.mov")
        try await Self.fixture(at: input)
        var options = CardSaveOptions(); options.keepLocation = false; options.keepCaptureTime = false
        try await LivePhotoMovie.copy(from: input, to: output, options: options)
        #expect(try await LivePhotoMovie.contentIdentifier(output) == "4AD60801-2BA5-4B9F-A69C-1F66A11EADCF")
        #expect(try await LivePhotoMovie.sampleDigest(input, type: .video) == LivePhotoMovie.sampleDigest(output, type: .video))
        #expect(try await LivePhotoMovie.sampleDigest(input, type: .audio) == LivePhotoMovie.sampleDigest(output, type: .audio))
        let asset = AVURLAsset(url: output)
        let metadata = try await asset.load(.metadata)
        #expect(!metadata.contains { $0.identifier?.rawValue.contains("location") == true })
        let track = try #require(try await asset.loadTracks(withMediaType: .metadata).first)
        let reader = try AVAssetReader(asset: asset)
        let result = AVAssetReaderTrackOutput(track: track, outputSettings: nil)
        reader.add(result)
        let adapter = AVAssetReaderOutputMetadataAdaptor(assetReaderTrackOutput: result)
        #expect(reader.startReading())
        let group = try #require(adapter.nextTimedMetadataGroup())
        #expect(group.items.count == 1)
        #expect(group.items[0].identifier?.rawValue.hasSuffix("still-image-time") == true)
        let sourcePhoto = folder.appendingPathComponent("original.heic")
        let renderedPhoto = folder.appendingPathComponent("edited.heic")
        let still = CIImage(color: CIColor(red: 0.5, green: 0.5, blue: 0.5)).cropped(to: CGRect(x: 0, y: 0, width: 64, height: 64))
        try CIContext().writeHEIFRepresentation(of: still, to: sourcePhoto, format: .RGBA8,
            colorSpace: CGColorSpace(name: CGColorSpace.displayP3)!, options: [:])
        options.format = .heic
        try await CardImageProcessor.shared.export(sourcePhoto, card: PhotoCard(), options: options, hdr: false,
            to: renderedPhoto, live: true, liveIdentifier: LivePhotoMovie.contentIdentifier(output))
        try await LivePhotoPair.validate(photo: renderedPhoto, movie: output)
    }

    @Test func allMetadataKeptCopiesMovieBytes() async throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let source = folder.appendingPathComponent("source.mov"), output = folder.appendingPathComponent("output.mov")
        let bytes = Data("retained video resource".utf8)
        try bytes.write(to: source)
        var options = CardSaveOptions(); options.keepLocation = true
        try await LivePhotoMovie.copy(from: source, to: output, options: options)
        #expect(try Data(contentsOf: output) == bytes)
    }

    private static func item(_ key: String, value: any NSCopying & NSObjectProtocol, dataType: String) -> AVMetadataItem {
        let item = AVMutableMetadataItem()
        item.identifier = AVMetadataIdentifier(rawValue: "mdta/" + key)
        item.value = value
        item.dataType = dataType
        return item
    }

    private static func fixture(at url: URL) async throws {
        let writer = try AVAssetWriter(outputURL: url, fileType: .mov)
        writer.metadata = [item("com.apple.quicktime.content.identifier", value: "4AD60801-2BA5-4B9F-A69C-1F66A11EADCF" as NSString, dataType: kCMMetadataBaseDataType_UTF8 as String),
                           item("com.apple.quicktime.location.ISO6709", value: "+22.30+114.20/" as NSString, dataType: kCMMetadataBaseDataType_UTF8 as String)]
        let video = AVAssetWriterInput(mediaType: .video, outputSettings: [AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: 64, AVVideoHeightKey: 64])
        let audio = AVAssetWriterInput(mediaType: .audio, outputSettings: [AVFormatIDKey: kAudioFormatMPEG4AAC, AVSampleRateKey: 48_000, AVNumberOfChannelsKey: 1])
        let pixels = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: video, sourcePixelBufferAttributes: [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB, kCVPixelBufferWidthKey as String: 64, kCVPixelBufferHeightKey as String: 64])
        let keys = ["com.apple.quicktime.still-image-time", "com.apple.quicktime.location.ISO6709"]
        let specs = keys.enumerated().map { index, key in
            [kCMMetadataFormatDescriptionMetadataSpecificationKey_Identifier as String: "mdta/" + key,
             kCMMetadataFormatDescriptionMetadataSpecificationKey_DataType as String: (index == 0 ? kCMMetadataBaseDataType_SInt8 : kCMMetadataBaseDataType_UTF8) as String]
        }
        var format: CMMetadataFormatDescription?
        #expect(CMMetadataFormatDescriptionCreateWithMetadataSpecifications(allocator: kCFAllocatorDefault, metadataType: kCMMetadataFormatType_Boxed, metadataSpecifications: specs as CFArray, formatDescriptionOut: &format) == noErr)
        let metadata = AVAssetWriterInput(mediaType: .metadata, outputSettings: nil, sourceFormatHint: format)
        let adapter = AVAssetWriterInputMetadataAdaptor(assetWriterInput: metadata)
        writer.add(video); writer.add(audio); writer.add(metadata)
        #expect(writer.startWriting())
        writer.startSession(atSourceTime: .zero)
        var stream = AudioStreamBasicDescription(mSampleRate: 48_000, mFormatID: kAudioFormatLinearPCM,
            mFormatFlags: kLinearPCMFormatFlagIsSignedInteger | kLinearPCMFormatFlagIsPacked,
            mBytesPerPacket: 2, mFramesPerPacket: 1, mBytesPerFrame: 2, mChannelsPerFrame: 1, mBitsPerChannel: 16, mReserved: 0)
        var audioFormat: CMAudioFormatDescription?
        #expect(CMAudioFormatDescriptionCreate(allocator: kCFAllocatorDefault, asbd: &stream, layoutSize: 0, layout: nil,
            magicCookieSize: 0, magicCookie: nil, extensions: nil, formatDescriptionOut: &audioFormat) == noErr)
        var block: CMBlockBuffer?
        let sampleCount = 14_400
        #expect(CMBlockBufferCreateWithMemoryBlock(allocator: kCFAllocatorDefault, memoryBlock: nil, blockLength: sampleCount * 2,
            blockAllocator: kCFAllocatorDefault, customBlockSource: nil, offsetToData: 0, dataLength: sampleCount * 2,
            flags: 0, blockBufferOut: &block) == kCMBlockBufferNoErr)
        #expect(CMBlockBufferFillDataBytes(with: 0, blockBuffer: try #require(block), offsetIntoDestination: 0, dataLength: sampleCount * 2) == kCMBlockBufferNoErr)
        var sample: CMSampleBuffer?
        var timing = CMSampleTimingInfo(duration: CMTime(value: 1, timescale: 48_000), presentationTimeStamp: .zero, decodeTimeStamp: .invalid)
        var sampleSize = 2
        #expect(CMSampleBufferCreateReady(allocator: kCFAllocatorDefault, dataBuffer: block, formatDescription: audioFormat,
            sampleCount: sampleCount, sampleTimingEntryCount: 1, sampleTimingArray: &timing, sampleSizeEntryCount: 1,
            sampleSizeArray: &sampleSize, sampleBufferOut: &sample) == noErr)
        while !audio.isReadyForMoreMediaData { try await Task.sleep(for: .milliseconds(2)) }
        #expect(audio.append(try #require(sample)))
        audio.markAsFinished()
        for frame in 0..<3 {
            while !video.isReadyForMoreMediaData { try await Task.sleep(for: .milliseconds(2)) }
            var buffer: CVPixelBuffer?
            #expect(CVPixelBufferCreate(kCFAllocatorDefault, 64, 64, kCVPixelFormatType_32ARGB, nil, &buffer) == kCVReturnSuccess)
            let pixel = try #require(buffer)
            CVPixelBufferLockBaseAddress(pixel, [])
            memset(CVPixelBufferGetBaseAddress(pixel), 180, CVPixelBufferGetDataSize(pixel))
            CVPixelBufferUnlockBaseAddress(pixel, [])
            #expect(pixels.append(pixel, withPresentationTime: CMTime(value: Int64(frame), timescale: 10)))
        }
        while !metadata.isReadyForMoreMediaData { try await Task.sleep(for: .milliseconds(2)) }
        #expect(adapter.append(AVTimedMetadataGroup(items: [
            item(keys[0], value: NSNumber(value: 0), dataType: kCMMetadataBaseDataType_SInt8 as String),
            item(keys[1], value: "+22.30+114.20/" as NSString, dataType: kCMMetadataBaseDataType_UTF8 as String)
        ], timeRange: CMTimeRange(start: CMTime(value: 1, timescale: 10), duration: CMTime(value: 1, timescale: 10)))))
        video.markAsFinished(); metadata.markAsFinished()
        await writer.finishWriting()
        #expect(writer.status == .completed)
    }
}
