import SwiftUI

@main
struct FuyaoPhotosApp: App {
    @State private var workspace = PhotoWorkspace()

    var body: some Scene {
        WindowGroup { ContentView(workspace: workspace) }
            .defaultSize(width: 1280, height: 820)
#if os(macOS)
            .commands { WorkspaceCommands(workspace: workspace) }
#endif
#if os(macOS)
            Settings { SettingsView().frame(minWidth: 760, idealWidth: 1100, minHeight: 560, idealHeight: 760) }
                .defaultSize(width: 1100, height: 760)
                .windowResizability(.contentMinSize)
#endif
    }
}

#if os(macOS)
/// macOS requires every toolbar command to also live in the menu bar; the photo pages'
/// open and save actions are surfaced here for the keyboard and Full Keyboard Access.
struct WorkspaceCommands: Commands {
    let workspace: PhotoWorkspace

    var body: some Commands {
        CommandGroup(replacing: .newItem) {
            Button("menu.open.photos") { workspace.requestOpenPhotos() }
                .keyboardShortcut("o")
            Button("package.import.action") { workspace.showingPackagePicker = true }
        }
        CommandGroup(after: .saveItem) {
            Button("menu.save.photos") { workspace.requestSave() }
                .keyboardShortcut("s")
                .disabled(!workspace.hasOpenPhotos)
        }
    }
}
#endif
