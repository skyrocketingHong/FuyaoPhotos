import SwiftUI

struct CardStyleSlider: View {
    @Binding var value: Double
    let range: ClosedRange<Double>
    let defaultValue: Double
    let label: LocalizedStringKey
    let minimumSymbol: String
    let maximumSymbol: String
    let formattedValue: String

    @ScaledMetric(relativeTo: .headline) private var valueLabelHeight: CGFloat = 26

    init(
        value: Binding<Double>,
        in range: ClosedRange<Double>,
        defaultValue: Double,
        label: LocalizedStringKey,
        minimumSymbol: String,
        maximumSymbol: String,
        formattedValue: String
    ) {
        _value = value
        self.range = range
        self.defaultValue = defaultValue
        self.label = label
        self.minimumSymbol = minimumSymbol
        self.maximumSymbol = maximumSymbol
        self.formattedValue = formattedValue
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(formattedValue)
                .font(.headline.monospacedDigit())
                .foregroundStyle(.yellow)
                .frame(maxWidth: .infinity, minHeight: valueLabelHeight, alignment: .leading)
                .accessibilityHidden(true)

            Slider(value: $value, in: range, neutralValue: defaultValue) {
                Text(label)
            } minimumValueLabel: {
                Image(systemName: minimumSymbol)
                    .frame(width: 22, height: 22)
                    .accessibilityHidden(true)
            } maximumValueLabel: {
                Image(systemName: maximumSymbol)
                    .frame(width: 22, height: 22)
                    .accessibilityHidden(true)
            } ticks: {
                SliderTick(defaultValue)
            }
            .accessibilityValue(Text(formattedValue))
        }
        .frame(maxWidth: 320, alignment: .leading)
    }
}
