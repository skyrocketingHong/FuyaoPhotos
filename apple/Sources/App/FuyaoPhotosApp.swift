import SwiftUI

@main
struct FuyaoPhotosApp: App {
    @State private var workspace = PhotoWorkspace()

    var body: some Scene {
        WindowGroup { ContentView(workspace: workspace) }
            .defaultSize(width: 1000, height: 800)
#if os(macOS)
            .commands { WorkspaceCommands(workspace: workspace) }
#endif
#if os(macOS)
            Settings { SettingsView().frame(minWidth: 460, idealWidth: 520, minHeight: 480) }
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
