import SwiftUI
#if os(iOS)
import UIKit
#endif

struct PhotoContinuousSlider: View {
    @Binding var value: Double
    let range: ClosedRange<Double>
    var reference: Double?
    var step: Double?

    var body: some View {
#if os(iOS)
        NativeSlider(value: $value, range: range, reference: reference, step: step)
            .frame(minHeight: 44)
#else
        if let step {
            Slider(value: $value, in: range, step: step).labelsHidden()
        } else {
            Slider(value: $value, in: range).labelsHidden()
        }
#endif
    }
}

#if os(iOS)
private struct NativeSlider: UIViewRepresentable {
    @Binding var value: Double
    let range: ClosedRange<Double>
    let reference: Double?
    let step: Double?
    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.layoutDirection) private var layoutDirection

    func makeUIView(context: Context) -> UISlider {
        let slider = UISlider()
        slider.isContinuous = true
        slider.setContentHuggingPriority(.defaultLow, for: .horizontal)
        slider.addTarget(context.coordinator, action: #selector(Coordinator.valueChanged(_:)), for: .valueChanged)
        return slider
    }

    func updateUIView(_ slider: UISlider, context: Context) {
        context.coordinator.parent = self
        slider.isEnabled = isEnabled
        slider.semanticContentAttribute = layoutDirection == .rightToLeft ? .forceRightToLeft : .forceLeftToRight
        slider.minimumValue = Float(range.lowerBound)
        slider.maximumValue = Float(range.upperBound)
        let fraction = reference.map { Float(($0 - range.lowerBound) / (range.upperBound - range.lowerBound)) }
        let positions: [Float] = fraction.map { [$0] } ?? [0, 1]
        let configuration = UISlider.TrackConfiguration(
            allowsTickValuesOnly: false,
            neutralValue: fraction ?? 0,
            enabledRange: 0...1,
            ticks: positions.map { .init(position: $0) }
        )
        if slider.trackConfiguration != configuration { slider.trackConfiguration = configuration }
        if abs(Double(slider.value) - value) > 0.0001 { slider.value = Float(value) }
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UISlider, context: Context) -> CGSize? {
        CGSize(width: proposal.width ?? max(44, uiView.intrinsicContentSize.width),
               height: max(44, uiView.intrinsicContentSize.height))
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject {
        var parent: NativeSlider
        init(_ parent: NativeSlider) { self.parent = parent }
        @objc func valueChanged(_ sender: UISlider) {
            let raw = Double(sender.value)
            let adjusted = parent.step.map { parent.range.lowerBound + ((raw - parent.range.lowerBound) / $0).rounded() * $0 } ?? raw
            parent.value = min(parent.range.upperBound, max(parent.range.lowerBound, adjusted))
        }
    }
}
#endif
