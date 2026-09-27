import SwiftUI

enum PhotoSharingMode: String, CaseIterable, Identifiable {
    case all, independent, partial
    var id: Self { self }
    var title: LocalizedStringKey { LocalizedStringKey("workspace.sharing." + rawValue) }
}

@MainActor @Observable final class WorkspacePreferences {
    static let shared = WorkspacePreferences()
    var startup: PhotoWorkspace.Tab = PhotoWorkspace.Tab(rawValue: UserDefaults.standard.string(forKey: "workspace.startup") ?? "cards") ?? .cards {
        didSet { UserDefaults.standard.set(startup.rawValue, forKey: "workspace.startup") }
    }
    var sharing: PhotoSharingMode = {
        if let raw = UserDefaults.standard.string(forKey: "workspace.sharing"), let value = PhotoSharingMode(rawValue: raw) { return value }
        return UserDefaults.standard.bool(forKey: "metadata.sharesCards") ? .partial : .independent
    }() {
        didSet { UserDefaults.standard.set(sharing.rawValue, forKey: "workspace.sharing") }
    }
    var sharedTabs: Set<PhotoWorkspace.Tab> = {
        guard let values = UserDefaults.standard.stringArray(forKey: "workspace.sharedTabs") else { return [.cards, .metadata] }
        return Set(values.compactMap(PhotoWorkspace.Tab.init(rawValue:)))
    }() {
        didSet { UserDefaults.standard.set(sharedTabs.map(\.rawValue), forKey: "workspace.sharedTabs") }
    }

    func shares(_ tab: PhotoWorkspace.Tab) -> Bool {
        PhotoWorkspace.Tab.photoTabs.contains(tab) && (sharing == .all || (sharing == .partial && sharedTabs.count >= 2 && sharedTabs.contains(tab)))
    }
}
