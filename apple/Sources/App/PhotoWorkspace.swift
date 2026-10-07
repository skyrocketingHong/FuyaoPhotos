import SwiftUI

@MainActor @Observable final class PhotoWorkspace {
    enum Tab: String, Hashable, CaseIterable, Identifiable {
        case map, cards, metadata, colors, settings
        var id: Self { self }
        static let photoTabs: [Self] = [.cards, .metadata, .colors]
        static let featureTabs: [Self] = [.map, .cards, .metadata, .colors]
        var title: LocalizedStringKey { self == .settings ? "settings.title" : LocalizedStringKey("tab." + rawValue) }
        var symbol: String {
            switch self {
            case .map: "map"
            case .cards: "photo.badge.plus"
            case .metadata: "info.circle"
            case .colors: "eyedropper.halffull"
            case .settings: "gearshape"
            }
        }
    }
    var selectedTab: Tab = WorkspacePreferences.shared.startup {
        didSet {
            guard oldValue != selectedTab else { return }
            for session in [cards, metadataSession, colorsSession] {
                session.cancelDeparturePresentation()
            }
        }
    }
    var showingPackagePicker = false
    var incomingPackage: PackageImportRequest?
    private var packageQueue: [URL] = []
    func importPackage(_ url: URL) {
        if incomingPackage == nil { incomingPackage = PackageImportRequest(url: url) }
        else if packageQueue.count < 50 { packageQueue.append(url) }
    }
    func nextPackage() {
        if !packageQueue.isEmpty { incomingPackage = PackageImportRequest(url: packageQueue.removeFirst()) }
    }
    var pendingAssetIDs: [String]?
    /// Photos handed off from another tab for the metadata page to open.
    var pendingMetadataAssetIDs: [String]?
    let cards = CardSession()
    let metadataEdits = MetadataState()
    private let metadataSession = CardSession()
    private let colorsSession = CardSession()

    init() {
        let edits = metadataEdits
        for session in [cards, metadataSession, colorsSession] {
            session.documentsDidClose = { [weak edits] identifiers in edits?.discard(identifiers) }
        }
    }

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

    /// The page whose photo picker the macOS File > Open command should present; consumed by that page.
    var openPickerRequest: Tab?
    /// The page whose save sheet the macOS File > Save command should present; consumed by that page.
    var saveSheetRequest: Tab?

    /// Menu-bar Open: route to the selected photo page, falling back to cards.
    func requestOpenPhotos() {
        let tab = Tab.photoTabs.contains(selectedTab) ? selectedTab : .cards
        if tab != selectedTab { selectedTab = tab }
        openPickerRequest = tab
    }

    /// Menu-bar Save: prefer the selected page when it can save, else cards, else metadata.
    func requestSave() {
        let savable: [Tab] = [.cards, .metadata]
        let candidate = savable.contains(selectedTab) ? selectedTab : nil
        let tab = ([candidate].compactMap { $0 } + savable).first { session(for: $0).current != nil }
        guard let tab else { return }
        if tab != selectedTab { selectedTab = tab }
        saveSheetRequest = tab
    }

    /// Whether any page that offers saving currently has a photo, for the menu-bar Save command.
    var hasOpenPhotos: Bool {
        [.cards, .metadata].contains { session(for: $0).current != nil }
    }
}
