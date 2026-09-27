import SwiftUI

@MainActor @Observable final class PhotoWorkspace {
    enum Tab: String, Hashable, CaseIterable, Identifiable {
        case map, cards, metadata, colors, settings
        var id: Self { self }
        static let photoTabs: [Self] = [.cards, .metadata, .colors]
        static let featureTabs: [Self] = [.map, .cards, .metadata, .colors]
        var title: LocalizedStringKey { self == .settings ? "settings.title" : LocalizedStringKey("tab." + rawValue) }
    }
    var selectedTab: Tab = WorkspacePreferences.shared.startup
    var pendingAssetIDs: [String]?
    /// Photos handed off from another tab for the metadata page to open.
    var pendingMetadataAssetIDs: [String]?
    let cards = CardSession()
    let metadataEdits = MetadataState()
    private let metadataSession = CardSession()
    private let colorsSession = CardSession()

    func session(for tab: Tab) -> CardSession {
        let preferences = WorkspacePreferences.shared
        let owner = preferences.shares(tab)
            ? Tab.photoTabs.first(where: preferences.shares) ?? tab : tab
        switch owner {
        case .metadata: return metadataSession
        case .colors: return colorsSession
        default: return cards
        }
    }

    func hasPendingEdits(in owner: CardSession) -> Bool {
        if owner === cards && cards.hasChanges { return true }
        guard owner === session(for: .metadata) else { return false }
        return owner.documents.contains(where: metadataEdits.hasChanges)
    }

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
