import Foundation
import CoreMedia
import CryptoKit

nonisolated struct LivePhotoSampleDigest {
    private var digest = SHA256()
    var value: SHA256.Digest { digest.finalize() }

    mutating func append(_ sample: CMSampleBuffer) throws {
        guard CMSampleBufferIsValid(sample), CMSampleBufferDataIsReady(sample) else { throw CardError.videoMetadata }
        let count = CMSampleBufferGetNumSamples(sample)
        guard let block = CMSampleBufferGetDataBuffer(sample) else {
            guard count == 0, CMSampleBufferGetImageBuffer(sample) == nil else { throw CardError.videoMetadata }
            return
        }
        let length = CMBlockBufferGetDataLength(block)
        guard length <= 64 * 1024 * 1024 else { throw CardError.tooLarge }
        guard count > 0, length > 0 else { throw CardError.videoMetadata }
        var bytes = Data(count: length)
        let status = bytes.withUnsafeMutableBytes { storage in
            CMBlockBufferCopyDataBytes(block, atOffset: 0, dataLength: length, destination: storage.baseAddress!)
        }
        guard status == kCMBlockBufferNoErr else { throw CardError.videoMetadata }

        // PCM samples can be regrouped during passthrough. Hash logical samples, not reader batches.
        try bytes.withUnsafeBytes { storage in
            var offset = 0
            for index in 0..<count {
                if index.isMultiple(of: 4096) { try Task.checkCancellation() }
                let size = CMSampleBufferGetSampleSize(sample, at: index)
                guard size > 0, size <= length - offset else { throw CardError.videoMetadata }
                var timing = CMSampleTimingInfo()
                guard CMSampleBufferGetSampleTimingInfo(sample, at: index, timingInfoOut: &timing) == noErr,
                      timing.presentationTimeStamp.isNumeric else { throw CardError.videoMetadata }
                let time = CMTimeConvertScale(timing.presentationTimeStamp, timescale: 1_000_000, method: .default)
                appendInteger(Int64(size))
                appendInteger(time.value)
                appendInteger(time.epoch)
                digest.update(bufferPointer: UnsafeRawBufferPointer(rebasing: storage[offset..<(offset + size)]))
                offset += size
            }
            guard offset == length else { throw CardError.videoMetadata }
        }
    }

    private mutating func appendInteger(_ value: Int64) {
        var encoded = value.bigEndian
        withUnsafeBytes(of: &encoded) { digest.update(bufferPointer: $0) }
    }
}
