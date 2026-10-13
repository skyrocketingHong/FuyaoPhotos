import SwiftUI

struct SaveProgressLabel: View {
    let title: LocalizedStringKey
    let active: Bool
    var completed = 0
    var total = 1
    var compact = false
    var saved = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var showsCount: Bool { active && (total > 1 || reduceMotion) }

    private var symbol: some View {
        OperationProgressSymbol(active: active, completed: total > 1 ? completed : nil,
            total: total, saved: saved, showsIcon: !compact || !showsCount)
    }

    var body: some View {
        Group {
            if compact {
                ZStack {
                    symbol
                    if showsCount {
                        let count = max(0, min(completed, max(1, total)))
                        Text(total > 1 ? "\(count)" : "\(count)/\(max(1, total))")
                            .font(.caption2.monospacedDigit())
                            .lineLimit(1)
                            .minimumScaleFactor(0.6)
                            .frame(width: 22)
                            .contentTransition(reduceMotion ? .identity : .numericText())
                    }
                }
                .frame(width: 24, height: 24)
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
