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

    private var atMinimum: Bool {
        value <= range.lowerBound + (range.upperBound - range.lowerBound) * 0.001
    }

    private var atMaximum: Bool {
        value >= range.upperBound - (range.upperBound - range.lowerBound) * 0.001
    }

    var body: some View {
        HStack(spacing: 8) {
            SliderEndpointIcon(symbol: minimumSymbol, active: atMinimum)
#if os(iOS)
            ContinuousReferenceSlider(value: $value, range: range, reference: defaultValue)
                .accessibilityLabel(Text(label))
                .accessibilityValue(Text(formattedValue))
#else
            Slider(value: $value, in: range) { Text(label) }
                .accessibilityValue(Text(formattedValue))
#endif
            SliderEndpointIcon(symbol: maximumSymbol, active: atMaximum)
            Text(formattedValue)
                .font(.headline.monospacedDigit())
                .foregroundStyle(.yellow)
                .frame(minWidth: 52, alignment: .trailing)
                .accessibilityHidden(true)
        }
        .frame(maxWidth: 320, alignment: .leading)
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
            .foregroundStyle(active ? Color.yellow : Color.secondary)
            .scaleEffect(active ? 1.12 : 1)
            .frame(width: 22, height: 22)
            .animation(reduceMotion ? nil : .snappy(duration: 0.22), value: active)
            .accessibilityHidden(true)
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
