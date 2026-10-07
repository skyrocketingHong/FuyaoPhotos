import SwiftUI

struct PhotoPreviewMenu<Content: View>: View {
    let title: LocalizedStringKey
    var systemImage = "ellipsis"
    @ViewBuilder let content: () -> Content

    var body: some View {
        Menu(content: content) {
            PhotoPreviewActionIcon(image: Image(systemName: systemImage))
#if os(iOS)
                .modifier(PhotoActionForeground())
#endif
        }
#if os(macOS)
        .menuStyle(.button)
#endif
        .menuIndicator(.hidden)
        .photoIconControlStyle()
        .accessibilityLabel(Text(title))
        .help(Text(title))
    }
}
