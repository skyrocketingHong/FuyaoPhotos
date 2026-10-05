import Foundation

nonisolated public enum ParticleMotion {
    public struct Grid: Equatable, Sendable {
        public let columns: Int
        public let rows: Int
        public var count: Int { columns * rows }
    }

    public struct Frame: Equatable, Sendable {
        public let x: Double
        public let y: Double
        public let scale: Double
        public let alpha: Double
    }

    nonisolated public static func grid(width: Double, height: Double) -> Grid {
        guard width.isFinite, height.isFinite, width > 0, height > 0 else { return Grid(columns: 0, rows: 0) }
        let cell = max(PhotoMotionTokens.minimumParticleSize, sqrt(width * height / Double(PhotoMotionTokens.maxParticles)))
        let columns = max(1, Int(min(Double(PhotoMotionTokens.maxParticles), floor(width / cell))))
        let rows = max(1, Int(min(Double(PhotoMotionTokens.maxParticles / columns), floor(height / cell))))
        return Grid(columns: columns, rows: rows)
    }

    nonisolated public static func frame(index: Int, columns: Int, progress: Double, reverse: Bool = false) -> Frame {
        let p = progress.isFinite ? min(1, max(0, progress)) : 0
        let column = max(0, index) % max(1, columns)
        let delay = Double(column) / Double(max(1, columns - 1)) * PhotoMotionTokens.particleStagger
        let age = min(1, max(0, (p - delay) / (1 - delay)))
        let fade = min(1, max(0, (age - 0.2) / 0.8))
        let direction: Double = reverse ? -1 : 1
        return Frame(
            x: direction * PhotoMotionTokens.particleTravel * (0.3 + random(index: index, channel: 1) * 0.7) * age,
            y: PhotoMotionTokens.particleLift * ((random(index: index, channel: 2) - 0.5) * age - age * age),
            scale: 1 - PhotoMotionTokens.particleShrink * age,
            alpha: 1 - fade * fade * (3 - 2 * fade)
        )
    }

    nonisolated private static func random(index: Int, channel: UInt64) -> Double {
        let seed = UInt64(max(0, index)) &* 3 &+ channel
        return Double((seed &* 1_103_515_245 &+ 12_345) & 0x7fffffff) / 2_147_483_647
    }

    nonisolated public static func progress(completed: Int, total: Int) -> Double {
        total > 0 ? Double(min(total, max(0, completed))) / Double(total) : 0
    }
}
