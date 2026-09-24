import SwiftUI
#if os(macOS)
import AppKit
#else
import UIKit
#endif

@MainActor enum PhotosApplication {
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
