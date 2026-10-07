import Foundation

nonisolated enum HeatmapSafeAreaPolicy {
    static func additional(required: CGFloat, resolved: CGFloat, applied: CGFloat) -> CGFloat {
        let inherited = max(0, resolved - applied)
        return max(0, required - inherited)
    }
}
