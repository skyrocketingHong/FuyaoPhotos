import SwiftUI

struct CircularIconButton: View {
    let title: LocalizedStringKey
    let systemImage: String
    let action: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(_ title: LocalizedStringKey, systemImage: String, action: @escaping () -> Void) {
        self.title = title
        self.systemImage = systemImage
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            PhotoPreviewActionIcon(image: Image(systemName: systemImage))
                .contentTransition(reduceMotion ? .identity : .symbolEffect(.replace))
                .animation(reduceMotion ? nil : .smooth(duration: 0.2), value: systemImage)
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
