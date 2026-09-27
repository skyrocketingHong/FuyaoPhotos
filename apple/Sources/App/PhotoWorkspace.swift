import SwiftUI

@MainActor @Observable final class PhotoWorkspace {
    enum Tab: Hashable { case map, cards, metadata, settings }
    var selectedTab: Tab = .map
    var pendingAssetIDs: [String]?
    /// Photos handed off from another tab for the metadata page to open.
    var pendingMetadataAssetIDs: [String]?
    let cards = CardSession()
    let metadataSession = MetadataSession()

    func editPhotos(_ ids: [String]) {
        pendingAssetIDs = ids
        selectedTab = .cards
    }

    /// Hands the given library photos to the metadata page and opens it.
    func openInMetadata(_ ids: [String]) {
        pendingMetadataAssetIDs = ids
        selectedTab = .metadata
    }
}
