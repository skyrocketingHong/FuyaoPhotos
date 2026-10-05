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
        OperationProgressSymbol(active: active, completed: total > 1 ? completed : nil, total: total, saved: saved)
    }

    var body: some View {
        Group {
            if compact {
                VStack(spacing: 2) {
                    symbol
                    if active {
                        Text("\(completed)/\(max(1, total))")
                            .font(.caption2.monospacedDigit())
                            .contentTransition(reduceMotion ? .identity : .numericText())
                    }
                }
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
