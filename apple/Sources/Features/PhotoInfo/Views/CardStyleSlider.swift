import SwiftUI
#if os(iOS)
import UIKit
#endif

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

            HStack(spacing: 8) {
                Image(systemName: minimumSymbol)
                    .frame(width: 22, height: 22)
                    .accessibilityHidden(true)
#if os(iOS)
                ContinuousReferenceSlider(value: $value, range: range, reference: defaultValue)
                    .accessibilityLabel(Text(label))
                    .accessibilityValue(Text(formattedValue))
#else
                Slider(value: $value, in: range) { Text(label) }
                    .accessibilityValue(Text(formattedValue))
#endif
                Image(systemName: maximumSymbol)
                    .frame(width: 22, height: 22)
                    .accessibilityHidden(true)
            }
        }
        .frame(maxWidth: 320, alignment: .leading)
    }
}

#if os(iOS)
private struct ContinuousReferenceSlider: UIViewRepresentable {
    @Binding var value: Double
    let range: ClosedRange<Double>
    let reference: Double

    func makeUIView(context: Context) -> UISlider {
        let slider = UISlider()
        slider.addTarget(context.coordinator, action: #selector(Coordinator.valueChanged(_:)), for: .valueChanged)
        return slider
    }

    func updateUIView(_ slider: UISlider, context: Context) {
        context.coordinator.parent = self
        slider.minimumValue = Float(range.lowerBound)
        slider.maximumValue = Float(range.upperBound)
        let fraction = Float((reference - range.lowerBound) / (range.upperBound - range.lowerBound))
        let configuration = UISlider.TrackConfiguration(
            allowsTickValuesOnly: false,
            neutralValue: fraction,
            enabledRange: 0...1,
            ticks: [.init(position: fraction)]
        )
        if slider.trackConfiguration != configuration { slider.trackConfiguration = configuration }
        if abs(Double(slider.value) - value) > 0.0001 { slider.value = Float(value) }
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject {
        var parent: ContinuousReferenceSlider
        init(_ parent: ContinuousReferenceSlider) { self.parent = parent }
        @objc func valueChanged(_ sender: UISlider) { parent.value = Double(sender.value) }
    }
}
#endif
