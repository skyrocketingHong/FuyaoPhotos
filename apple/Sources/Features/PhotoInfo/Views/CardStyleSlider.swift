import SwiftUI

struct CardStyleSlider: View {
    @Binding var value: Double
    let range: ClosedRange<Double>
    let defaultValue: Double
    let label: LocalizedStringKey
    let minimumSymbol: String
    let maximumSymbol: String
    let formattedValue: String
    var showsLabels = true
    @ScaledMetric(relativeTo: .headline) private var valueSlotWidth: CGFloat = 56

    private var atMinimum: Bool {
        value <= range.lowerBound + (range.upperBound - range.lowerBound) * 0.001
    }

    private var atMaximum: Bool {
        value >= range.upperBound - (range.upperBound - range.lowerBound) * 0.001
    }

    var body: some View {
        VStack(spacing: 4) {
            if showsLabels {
                HStack(spacing: 8) {
                    SliderEndpointIcon(symbol: minimumSymbol, active: atMinimum)
                    Spacer(minLength: 8)
                    Text(formattedValue)
                        .font(.headline.monospacedDigit())
                        .foregroundStyle(.secondary)
                        .frame(width: valueSlotWidth, alignment: .center)
                        .accessibilityHidden(true)
                    Spacer(minLength: 8)
                    SliderEndpointIcon(symbol: maximumSymbol, active: atMaximum)
                }
            }
            PhotoContinuousSlider(value: $value, range: range, reference: defaultValue)
                .accessibilityLabel(Text(label))
                .accessibilityValue(Text(formattedValue))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(maxHeight: .infinity)
    }
}

/// Mirrors the system slider behavior of highlighting a label once the thumb reaches its end.
private struct SliderEndpointIcon: View {
    let symbol: String
    let active: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Image(systemName: symbol)
            .foregroundStyle(active ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
            .scaleEffect(active ? 1.12 : 1)
            .frame(width: 22, height: 22)
            .animation(reduceMotion ? nil : .snappy(duration: 0.22), value: active)
            .accessibilityHidden(true)
    }
}
