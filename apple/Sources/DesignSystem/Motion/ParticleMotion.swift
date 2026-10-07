import Foundation

nonisolated public enum ParticleMotion {
    nonisolated public static func progress(completed: Int, total: Int) -> Double {
        total > 0 ? Double(min(total, max(0, completed))) / Double(total) : 0
    }
}
