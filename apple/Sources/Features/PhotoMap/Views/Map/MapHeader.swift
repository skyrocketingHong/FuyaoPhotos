import SwiftUI

struct MapHeader<Controls: View>: View {
    let count: Int?
    @ViewBuilder var controls: Controls
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            if let count {
                Text("photo.count.visible \(count)")
                    .font(.caption.monospacedDigit())
                    .contentTransition(.numericText(value: Double(count)))
                    .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: count)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .frame(maxHeight: .infinity)
                    .glassEffect(in: .capsule)
                    .layoutPriority(1)
            }
            Spacer(minLength: 8)
            HStack(alignment: .center, spacing: 8) { controls }
                .fixedSize()
        }
        // The native controls determine the row height; the count capsule fills it.
        .fixedSize(horizontal: false, vertical: true)
        .padding(.horizontal, 20)
        .padding(.top, 12)
    }
}
