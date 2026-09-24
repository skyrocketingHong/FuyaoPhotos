import Foundation
import Observation
import CoreLocation

@MainActor @Observable final class CardDocument: Identifiable {
    let id = UUID()
    let sourceURL: URL
    let sourceMovieURL: URL?
    let originalName: String
    let assetIdentifier: String?
    let metadata: CardPhotoMetadata
    private(set) var defaultCard: PhotoCard
    private let workingDirectory: PhotoWorkingDirectory
    var isLive: Bool { sourceMovieURL != nil }
    var card: PhotoCard {
        didSet {
            if card[.location] != oldValue[.location] { locationRevision &+= 1 }
        }
    }
    private(set) var locationRevision: UInt64 = 0
    var savedCard: PhotoCard?
    var exportURL: URL?
    var location: CLLocation? {
        guard let lat = metadata.latitude, let lon = metadata.longitude else { return nil }
        return CLLocation(latitude: lat, longitude: lon)
    }
    var hasChanges: Bool { savedCard != card }

    init(resources: PhotoSourceResources, metadata: CardPhotoMetadata) {
        sourceURL = resources.image; sourceMovieURL = resources.movie
        workingDirectory = PhotoWorkingDirectory(resources.image.deletingLastPathComponent())
        originalName = resources.originalName; assetIdentifier = resources.assetIdentifier
        self.metadata = metadata; defaultCard = metadata.card; card = metadata.card
    }

    func applyResolvedLocation(_ value: String) {
        guard !value.isEmpty else { return }
        defaultCard[.location] = value
        card[.location] = value
    }
}
