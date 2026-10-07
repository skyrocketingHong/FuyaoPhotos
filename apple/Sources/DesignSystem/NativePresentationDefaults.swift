import SwiftUI

struct NativePresentationDefaults: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(nil)
            .fontWeight(nil)
            .fontDesign(nil)
            .controlSize(.regular)
            .buttonStyle(.automatic)
            .labelStyle(.automatic)
            .tint(.accentColor)
    }
}
