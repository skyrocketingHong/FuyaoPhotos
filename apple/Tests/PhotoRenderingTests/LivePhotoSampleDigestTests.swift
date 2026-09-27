import Testing
import Foundation
import CoreMedia
import AudioToolbox
@testable import PhotoRenderingCore

struct LivePhotoSampleDigestTests {
    @Test func pcmBatchBoundariesDoNotChangeDigest() throws {
        let bytes = Array(UInt8(0)..<32)
        var whole = LivePhotoSampleDigest(), split = LivePhotoSampleDigest()
        try whole.append(pcm([bytes]))
        try split.append(pcm([Array(bytes[..<8])]))
        try split.append(pcm([Array(bytes[8...])], start: 4))
        #expect(whole.value == split.value)

        var shifted = LivePhotoSampleDigest(), changed = LivePhotoSampleDigest()
        try shifted.append(pcm([bytes], start: 1))
        var changedBytes = bytes
        changedBytes[15] ^= 0xff
        try changed.append(pcm([changedBytes]))
        #expect(whole.value != shifted.value)
        #expect(whole.value != changed.value)
    }

    @Test func fragmentedBlockHasTheSameLogicalBytes() throws {
        let bytes = Array(UInt8(0)..<32)
        let fragmented = try pcm([Array(bytes[..<7]), Array(bytes[7..<19]), Array(bytes[19...])])
        let block = try #require(CMSampleBufferGetDataBuffer(fragmented))
        #expect(!CMBlockBufferIsRangeContiguous(block, atOffset: 0, length: bytes.count))
        var actual = LivePhotoSampleDigest(), expected = LivePhotoSampleDigest()
        try actual.append(fragmented)
        try expected.append(pcm([bytes]))
        #expect(actual.value == expected.value)
    }

    @Test func markersAreSkippedButInvalidOrMissingPayloadsFail() throws {
        var digest = LivePhotoSampleDigest()
        let empty = digest.value
        try CMReadySampleBuffer<Never>(markerAt: .zero).withUnsafeSampleBuffer { try digest.append($0) }
        #expect(digest.value == empty)

        let invalid = try pcm([[1, 2]])
        #expect(CMSampleBufferInvalidate(invalid) == noErr)
        #expect(throws: (any Error).self) { try digest.append(invalid) }

        let missing = try pcm([[1, 2]], omitData: true)
        #expect(throws: (any Error).self) { try digest.append(missing) }
    }

    private func pcm(_ chunks: [[UInt8]], start: Int64 = 0, omitData: Bool = false) throws -> CMSampleBuffer {
        var block: CMBlockBuffer?
        try #require(CMBlockBufferCreateEmpty(allocator: kCFAllocatorDefault, capacity: UInt32(chunks.count),
            flags: 0, blockBufferOut: &block) == kCMBlockBufferNoErr)
        let buffer = try #require(block)
        var offset = 0
        for chunk in chunks {
            try #require(CMBlockBufferAppendMemoryBlock(buffer, memoryBlock: nil, length: chunk.count,
                blockAllocator: kCFAllocatorDefault, customBlockSource: nil, offsetToData: 0,
                dataLength: chunk.count, flags: 0) == kCMBlockBufferNoErr)
            let status = chunk.withUnsafeBytes {
                CMBlockBufferReplaceDataBytes(with: $0.baseAddress!, blockBuffer: buffer,
                    offsetIntoDestination: offset, dataLength: chunk.count)
            }
            try #require(status == kCMBlockBufferNoErr)
            offset += chunk.count
        }
        var stream = AudioStreamBasicDescription(mSampleRate: 48_000, mFormatID: kAudioFormatLinearPCM,
            mFormatFlags: kLinearPCMFormatFlagIsSignedInteger | kLinearPCMFormatFlagIsPacked,
            mBytesPerPacket: 2, mFramesPerPacket: 1, mBytesPerFrame: 2, mChannelsPerFrame: 1,
            mBitsPerChannel: 16, mReserved: 0)
        var format: CMAudioFormatDescription?
        try #require(CMAudioFormatDescriptionCreate(allocator: kCFAllocatorDefault, asbd: &stream,
            layoutSize: 0, layout: nil, magicCookieSize: 0, magicCookie: nil, extensions: nil,
            formatDescriptionOut: &format) == noErr)
        var timing = CMSampleTimingInfo(duration: CMTime(value: 1, timescale: 48_000),
            presentationTimeStamp: CMTime(value: start, timescale: 48_000), decodeTimeStamp: .invalid)
        var size = 2
        var sample: CMSampleBuffer?
        try #require(CMSampleBufferCreateReady(allocator: kCFAllocatorDefault, dataBuffer: omitData ? nil : buffer,
            formatDescription: format, sampleCount: offset / size, sampleTimingEntryCount: 1,
            sampleTimingArray: &timing, sampleSizeEntryCount: 1, sampleSizeArray: &size,
            sampleBufferOut: &sample) == noErr)
        return try #require(sample)
    }
}
