import SwiftUI

struct OperationProgressSymbol: View {
    let active: Bool
    var completed: Int?
    var total = 0
    var saved = false
    var showsIcon = true
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var fraction = 0.0
    @State private var started = ProcessInfo.processInfo.systemUptime

    var body: some View {
        ZStack {
            if showsIcon {
                Image(systemName: saved && !active ? "checkmark.circle.fill" : "square.and.arrow.down")
                    .resizable().scaledToFit()
                    .padding(active && (!reduceMotion || completed != nil) ? 6 : 3)
                    .contentTransition(reduceMotion ? .identity : .symbolEffect(.replace))
            }
            if active && (!reduceMotion || completed != nil) {
                TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion || scenePhase != .active)) { _ in
                    let elapsed = ProcessInfo.processInfo.systemUptime - started
                    let period = Double(PhotoMotionTokens.rotationMillis) / 1000
                    let rotation = reduceMotion ? 0 : elapsed.truncatingRemainder(dividingBy: period) / period * 360
                    Circle().stroke(.foreground.opacity(0.18), lineWidth: 1.8)
                        .overlay {
                            Circle().trim(from: 0, to: completed == nil ? 0.25 : max(reduceMotion ? 0 : 1.0 / 30, fraction))
                                .stroke(.foreground, style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
                                .rotationEffect(.degrees(rotation - 90))
                        }
                        .padding(1)
                }
            }
        }
        .frame(width: 28, height: 28)
        .animation(reduceMotion ? nil : .smooth(duration: Double(PhotoMotionTokens.controlMillis) / 1000), value: active)
        .onChange(of: completed, initial: true) { _, _ in updateProgress() }
        .onChange(of: total) { _, _ in updateProgress() }
        .onChange(of: active) { _, new in if new { started = ProcessInfo.processInfo.systemUptime }; updateProgress() }
        .accessibilityHidden(true)
    }

    private func updateProgress() {
        let target = ParticleMotion.progress(completed: completed ?? 0, total: total)
        withAnimation(reduceMotion || target < fraction ? nil : .easeOut(duration: Double(PhotoMotionTokens.progressMillis) / 1000)) {
            fraction = target
        }
    }
}
