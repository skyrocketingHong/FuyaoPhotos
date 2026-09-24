import SwiftUI

struct ProcessingVeil: ViewModifier {
    let active: Bool
    var pulse = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .blur(radius: active ? 2.5 : 0)
            .overlay {
                if active {
                    Color.black.opacity(0.18)
                        .allowsHitTesting(false)
                    Image(systemName: "viewfinder")
                        .font(.title2.weight(.medium))
                        .foregroundStyle(.yellow)
                        .shadow(color: .yellow.opacity(0.42), radius: 9)
                        .symbolEffect(.pulse, isActive: pulse && !reduceMotion)
                        .accessibilityLabel(Text("card.preview.updating"))
                        .allowsHitTesting(false)
                }
            }
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.22), value: active)
    }
}
