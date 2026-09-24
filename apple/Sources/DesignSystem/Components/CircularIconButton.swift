import SwiftUI

struct CircularIconButton: View {
    let title: LocalizedStringKey
    let systemImage: String
    let action: () -> Void

    init(_ title: LocalizedStringKey, systemImage: String, action: @escaping () -> Void) {
        self.title = title
        self.systemImage = systemImage
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .frame(width: 20, height: 20)
        }
#if os(macOS)
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
