import Foundation
import Photos

@MainActor enum LivePhotoPair {
    static func validate(photo: URL, movie: URL) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHLivePhoto.request(withResourceFileURLs: [photo, movie], placeholderImage: nil, targetSize: .zero, contentMode: .aspectFit) { result, info in
                guard (info[PHLivePhotoInfoIsDegradedKey] as? Bool) != true else { return }
                if result != nil { continuation.resume() }
                else { continuation.resume(throwing: CardError.livePairing) }
            }
        }
    }
}
