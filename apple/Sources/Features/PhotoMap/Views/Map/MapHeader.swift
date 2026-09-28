import SwiftUI

struct MapHeader<Controls: View>: View {
    let count: Int?
    @ViewBuilder var controls: Controls
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            if let count {
                VStack(spacing: 0) {
                    Text(count, format: .number)
                        .font(.title3.weight(.semibold).monospacedDigit())
                        .lineLimit(1)
                        .minimumScaleFactor(0.4)
                        .allowsTightening(true)
                        .contentTransition(.numericText(value: Double(count)))
                        .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: count)
                    Text("photo.count.unit")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 2)
                .frame(maxHeight: .infinity)
                .glassEffect(in: .capsule)
                .layoutPriority(1)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text("photo.count.visible \(count)"))
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

struct MapScaleContentAlignment: ViewModifier {
    @ScaledMetric(relativeTo: .caption) private var legendOffset = 3.0

    func body(content: Content) -> some View {
#if os(iOS)
        // The native legend's visible center sits below the control's frame center.
        content.alignmentGuide(VerticalAlignment.center) { $0[VerticalAlignment.center] + legendOffset }
#else
        content
#endif
    }
}
