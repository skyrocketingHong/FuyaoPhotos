import AVFoundation

actor LivePhotoStream {
    nonisolated enum Channel {
        case samples(AVAssetReaderOutput.Provider<CMReadySampleBuffer<CMSampleBuffer.DynamicContent>>, AVAssetWriterInput.SampleBufferReceiver)
        case metadata(AVAssetReaderOutput.Provider<AVTimedMetadataGroup>, AVAssetWriterInput.MetadataReceiver)
    }
    private let channel: Channel
    init(_ channel: sending Channel) { self.channel = channel }

    func copy(options: CardSaveOptions) async throws {
        switch channel {
        case .samples(let provider, let receiver):
            while let sample = try await provider.next() {
                try Task.checkCancellation()
                try await receiver.append(sample)
            }
            receiver.finish()
        case .metadata(let provider, let receiver):
            while let group = try await provider.next() {
                try Task.checkCancellation()
                let items = group.items.filter { LivePhotoMovie.keep($0.identifier?.rawValue ?? "", options: options) }
                if !items.isEmpty { try await receiver.append(AVTimedMetadataGroup(items: items, timeRange: group.timeRange)) }
            }
            receiver.finish()
        }
    }
}
