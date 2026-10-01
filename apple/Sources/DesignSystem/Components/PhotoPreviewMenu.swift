import SwiftUI

struct PhotoPreviewMenu<Content: View>: View {
    let title: LocalizedStringKey
    var systemImage = "ellipsis"
    @ViewBuilder let content: () -> Content

    var body: some View {
        Menu(content: content) {
            PhotoPreviewActionIcon(image: Image(systemName: systemImage))
        }
#if os(macOS)
        .menuStyle(.button)
        .menuIndicator(.hidden)
        .buttonStyle(.bordered)
        .controlSize(.regular)
#else
        .buttonStyle(.glass)
        .controlSize(.large)
#endif
        .buttonBorderShape(.circle)
        .accessibilityLabel(Text(title))
        .help(Text(title))
        .padding(6)
    }
}
