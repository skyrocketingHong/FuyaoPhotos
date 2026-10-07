import SwiftUI

struct SaveProgressLabel: View {
    let title: LocalizedStringKey
    let active: Bool
    var completed = 0
    var total = 1
    var compact = false
    var saved = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var symbol: some View {
        OperationProgressSymbol(active: active, completed: total > 1 ? completed : nil,
            total: total, saved: saved, showsIcon: !compact || !active)
    }

    var body: some View {
        Group {
            if compact {
                ZStack {
                    symbol
                    if active {
                        Text("\(completed)/\(max(1, total))")
                            .font(.caption2.monospacedDigit())
                            .lineLimit(1)
                            .minimumScaleFactor(0.6)
                            .frame(width: 24)
                            .contentTransition(reduceMotion ? .identity : .numericText())
                    }
                }
                .frame(width: 28, height: 28)
            } else {
                HStack(spacing: 8) {
                    symbol
                    if active { Text("card.saving \(completed) \(max(1, total))").monospacedDigit() }
                    else { Text(title) }
                }
            }
        }
        .animation(reduceMotion ? nil : .smooth(duration: 0.2), value: completed)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(title))
        .accessibilityValue(active ? Text("card.saving \(completed) \(max(1, total))") : Text(""))
    }
}
