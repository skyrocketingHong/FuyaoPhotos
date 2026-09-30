import SwiftUI

struct PackageImportButton: View {
    @Environment(PhotoWorkspace.self) private var workspace
    var body: some View {
        Button("package.import.action", systemImage: "square.and.arrow.down") { workspace.showingPackagePicker = true }
    }
}
