import SwiftUI
#if os(macOS)
import AppKit
#else
import UIKit
#endif

@MainActor enum PhotosApplication {
    /// Asset navigation is an undocumented Photos route. A successful dispatch
    /// confirms only that Photos accepted the URL, not that it selected the asset.
    @discardableResult static func open(assetIdentifier: String) async -> Bool {
        guard let uuid = assetIdentifier.split(separator: "/").first,
              UUID(uuidString: String(uuid)) != nil else { return false }
        var link = URLComponents()
#if os(macOS)
        link.scheme = "photos"
#else
        link.scheme = "photos-navigation"
#endif
        link.host = "asset"
        link.queryItems = [URLQueryItem(name: "identifier", value: assetIdentifier),
                           URLQueryItem(name: "uuid", value: String(uuid))]
        guard let url = link.url else { return false }
#if os(macOS)
        return NSWorkspace.shared.open(url)
#else
        return await UIApplication.shared.open(url)
#endif
    }

    @discardableResult static func open() async -> Bool {
#if os(macOS)
        guard let app = NSWorkspace.shared.urlForApplication(withBundleIdentifier: "com.apple.Photos") else { return false }
        do { _ = try await NSWorkspace.shared.openApplication(at: app, configuration: NSWorkspace.OpenConfiguration()); return true }
        catch { return false }
#else
        guard let url = URL(string: "photos-redirect://") else { return false }
        return await UIApplication.shared.open(url)
#endif
    }
}
