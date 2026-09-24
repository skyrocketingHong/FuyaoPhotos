import SwiftUI

@MainActor @Observable final class PhotoWorkspace {
    enum Tab: Hashable { case map, cards, settings }
    var selectedTab: Tab = .map
    var pendingAssetIDs: [String]?
    let cards = CardSession()

    func editPhotos(_ ids: [String]) {
        pendingAssetIDs = ids
        selectedTab = .cards
    }
}
