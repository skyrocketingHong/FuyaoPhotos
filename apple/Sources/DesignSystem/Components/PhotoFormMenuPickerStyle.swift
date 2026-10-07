import SwiftUI

private struct PhotoFormMenuPickerStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .pickerStyle(.menu)
            .tint(.secondary)
    }
}

extension View {
    func photoFormMenuPickerStyle() -> some View { modifier(PhotoFormMenuPickerStyle()) }
}
