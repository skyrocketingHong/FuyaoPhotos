import SwiftUI

@main
struct FuyaoPhotosApp: App {
    var body: some Scene {
        WindowGroup { ContentView() }
            .defaultSize(width: 1000, height: 800)
#if os(macOS)
        Settings { SettingsView().frame(minWidth: 460, idealWidth: 520, minHeight: 480) }
#endif
    }
}
