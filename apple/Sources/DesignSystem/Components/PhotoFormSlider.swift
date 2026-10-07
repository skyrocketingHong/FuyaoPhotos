import SwiftUI

struct PhotoFormSlider: View {
    let title: LocalizedStringKey
    @Binding var value: Double
    let range: ClosedRange<Double>
    let minimumLabel: LocalizedStringKey
    let maximumLabel: LocalizedStringKey
    var showsTitle = true
    var step: Double?
    var formattedValue: String?
    var maximumValueText: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if showsTitle {
                HStack(alignment: .firstTextBaseline) {
                    Text(title).foregroundStyle(.primary)
                    if let formattedValue {
                        Spacer(minLength: 12)
                        Text(maximumValueText ?? formattedValue)
                            .hidden()
                            .overlay(alignment: .trailing) { Text(formattedValue) }
                            .font(.body.monospacedDigit())
                            .foregroundStyle(.secondary)
                            .fixedSize()
                    }
                }
                .accessibilityHidden(true)
            }
            VStack(spacing: 4) {
                HStack(alignment: .firstTextBaseline) {
                    Text(minimumLabel)
                    Spacer(minLength: 12)
                    Text(maximumLabel).multilineTextAlignment(.trailing)
                }
                .font(.body)
                .foregroundStyle(.primary)
                .accessibilityHidden(true)
                PhotoContinuousSlider(value: $value, range: range, step: step)
                    .accessibilityLabel(Text(title))
                    .accessibilityValue(Text(formattedValue ?? value.formatted(.number.precision(.fractionLength(0...2)))))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
