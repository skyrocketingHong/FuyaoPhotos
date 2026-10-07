import SwiftUI

enum PhotoActionProminence {
    case primary
    case secondary
}

private struct PhotoActionStyle: ViewModifier {
    let prominence: PhotoActionProminence

    func body(content: Content) -> some View {
        Group {
            switch prominence {
            case .primary: content.buttonStyle(.borderedProminent)
            case .secondary: content.buttonStyle(.bordered)
            }
        }
        .tint(.accentColor)
        .controlSize(.regular)
    }
}

private struct PhotoIconControlStyle: ViewModifier {
    let prominence: PhotoActionProminence

    func body(content: Content) -> some View {
        Group {
#if os(macOS)
            content.buttonStyle(.bordered).controlSize(.regular)
#else
            Group {
                switch prominence {
                case .primary: content.buttonStyle(.glassProminent)
                case .secondary: content.buttonStyle(.glass)
                }
            }
            .controlSize(.regular)
#endif
        }
        .tint(.accentColor)
        .buttonBorderShape(.circle)
#if os(iOS)
        .frame(minWidth: 44, minHeight: 44)
#endif
        .padding(2)
    }
}

struct PhotoActionForeground: ViewModifier {
    @Environment(\.isEnabled) private var isEnabled

    func body(content: Content) -> some View {
        content.foregroundStyle(isEnabled ? Color.accentColor : Color.secondary)
    }
}

extension View {
    func photoActionStyle(_ prominence: PhotoActionProminence) -> some View {
        modifier(PhotoActionStyle(prominence: prominence))
    }

    func photoIconControlStyle(_ prominence: PhotoActionProminence = .secondary) -> some View {
        modifier(PhotoIconControlStyle(prominence: prominence))
    }
}
