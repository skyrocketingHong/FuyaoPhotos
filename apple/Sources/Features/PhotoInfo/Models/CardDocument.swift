import Foundation
import Observation
import CoreLocation

@MainActor @Observable final class CardDocument: Identifiable {
    let id = UUID()
    private(set) var sourceURL: URL
    let sourceMovieURL: URL?
    let originalName: String
    let assetIdentifier: String?
    private(set) var metadata: CardPhotoMetadata
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
    var exportIsMotionPhoto = false
    var location: CLLocation? {
        guard let lat = metadata.latitude, let lon = metadata.longitude else { return nil }
        return CLLocation(latitude: lat, longitude: lon)
    }
    var hasChanges: Bool { savedCard != card }

    init(resources: PhotoSourceResources, metadata: CardPhotoMetadata, preferLensPixelCount: Bool = false) {
        sourceURL = resources.image; sourceMovieURL = resources.movie
        workingDirectory = PhotoWorkingDirectory(resources.image.deletingLastPathComponent())
        originalName = resources.originalName; assetIdentifier = resources.assetIdentifier
        self.metadata = metadata; defaultCard = metadata.card; card = metadata.card
        if preferLensPixelCount, let pixels = metadata.lensImageSize { card[.imageSize] = pixels }
    }

    func applyResolvedLocation(_ value: String) {
        guard !value.isEmpty else { return }
        defaultCard[.location] = value
        card[.location] = value
    }

    func useFileImageSize() {
        card[.imageSize] = metadata.card[.imageSize]
    }

    func useLensImageSize() {
        guard let value = metadata.lensImageSize else { return }
        card[.imageSize] = value
    }

    func restoreField(_ field: CardField, preferLensPixelCount: Bool) {
        card[field] = field == .imageSize && preferLensPixelCount
            ? metadata.lensImageSize ?? defaultCard[field] : defaultCard[field]
    }

    func restoreInformation(preferLensPixelCount: Bool) {
        for field in CardField.allCases { restoreField(field, preferLensPixelCount: preferLensPixelCount) }
    }

    func applyMetadataUpdate(source: URL, metadata: CardPhotoMetadata) {
        let keepsCoordinates = self.metadata.latitude != nil && self.metadata.longitude != nil
            && self.metadata.latitude == metadata.latitude && self.metadata.longitude == metadata.longitude
        let resolvedLocation = defaultCard[.location]
        sourceURL = source
        self.metadata = metadata
        defaultCard = metadata.card
        if keepsCoordinates && defaultCard[.location].isEmpty {
            defaultCard[.location] = resolvedLocation
        }
        locationRevision &+= 1
    }
}
