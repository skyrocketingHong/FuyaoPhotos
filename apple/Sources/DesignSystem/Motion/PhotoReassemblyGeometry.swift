import Foundation
import CoreGraphics

nonisolated public enum PhotoReassemblyGeometry {
    public struct Grid: Equatable, Sendable {
        public let columns: Int
        public let rows: Int
        public var count: Int { columns * rows }
    }

    public struct Particle: Equatable, Sendable {
        public let center: SIMD2<Float>
        public let size: SIMD2<Float>
        public let colorFraction: Float
    }

    public static func grid(source: CGRect, target: CGRect) -> Grid? {
        guard valid(source), valid(target) else { return nil }
        let limit = PhotoMotionTokens.reassemblyMaxParticles
        let cell = PhotoMotionTokens.reassemblyCellSize
        guard limit > 0, cell > 0 else { return nil }
        let width = max(source.width, target.width)
        let height = max(source.height, target.height)
        var columns = max(1, Int(min(Double(limit), ceil(width / cell))))
        var rows = max(1, Int(min(Double(limit), ceil(height / cell))))
        if columns * rows > limit {
            let scale = sqrt(Double(limit) / Double(columns * rows))
            columns = max(1, Int(floor(Double(columns) * scale)))
            rows = max(1, Int(floor(Double(rows) * scale)))
            rows = min(rows, limit / columns)
        }
        return Grid(columns: columns, rows: rows)
    }

    public static func valid(_ rect: CGRect) -> Bool {
        rect.width > 0 && rect.height > 0 &&
            [rect.minX, rect.minY, rect.width, rect.height, rect.maxX, rect.maxY].allSatisfy {
                $0.isFinite && Float($0).isFinite
            }
    }

    public static func scatterRadius(source: CGRect) -> Float {
        Float(min(64, max(16, min(source.width, source.height) * PhotoMotionTokens.reassemblyScatter)))
    }

    public static func particle(index: Int, grid: Grid, source: CGRect, target: CGRect,
                                progress: Float) -> Particle? {
        guard index >= 0, index < grid.count, valid(source), valid(target), progress.isFinite else { return nil }
        let t = min(1, max(0, progress))
        let travel = smoothstep(Float(PhotoMotionTokens.reassemblyTravelStart), 1, t)
        let spread: Float = t <= 0 || t >= 1 ? 0 : sin(.pi * t)
        let resolution = SIMD2<Float>(Float(grid.columns), Float(grid.rows))
        let uv = (SIMD2<Float>(Float(index % grid.columns), Float(index / grid.columns)) + SIMD2(repeating: 0.5)) / resolution
        let sourceSize = SIMD2<Float>(Float(source.width), Float(source.height))
        let targetSize = SIMD2<Float>(Float(target.width), Float(target.height))
        let sourceCenter = SIMD2<Float>(Float(source.minX), Float(source.minY)) + uv * sourceSize
        let targetCenter = SIMD2<Float>(Float(target.minX), Float(target.minY)) + uv * targetSize
        let center = sourceCenter + (targetCenter - sourceCenter) * travel + offset(index: index) * scatterRadius(source: source) * spread
        let size = (sourceSize + (targetSize - sourceSize) * travel) / resolution * (1 - 0.55 * spread)
        return Particle(center: center, size: size,
            colorFraction: smoothstep(Float(PhotoMotionTokens.reassemblyColorStart), Float(PhotoMotionTokens.reassemblyColorEnd), t))
    }

    private static func smoothstep(_ start: Float, _ end: Float, _ value: Float) -> Float {
        let x = min(1, max(0, (value - start) / (end - start)))
        return x * x * (3 - 2 * x)
    }

    private static func offset(index: Int) -> SIMD2<Float> {
        var hash = UInt32(index) &* 747_796_405 &+ 2_891_336_453
        hash = (hash ^ (hash >> 16)) &* 2_246_822_519
        hash ^= hash >> 13
        let angle = 2 * Float.pi * Float(hash & 65_535) / 65_535
        let radius = 0.35 + 0.65 * Float((hash >> 16) & 65_535) / 65_535
        return SIMD2(cos(angle), sin(angle)) * radius
    }
}

/// Five float4 slots keep the Swift and Metal constant-buffer layouts identical.
nonisolated public struct PhotoReassemblyUniforms: Sendable {
    public var sourceRect: SIMD4<Float>
    public var targetRect: SIMD4<Float>
    public var canvasAndGrid: SIMD4<Float>
    public var animation: SIMD4<Float>
    public var colorAndPadding: SIMD4<Float>

    public init(source: CGRect, target: CGRect, canvas: CGSize, grid: PhotoReassemblyGeometry.Grid, progress: Float) {
        sourceRect = SIMD4(Float(source.minX), Float(source.minY), Float(source.width), Float(source.height))
        targetRect = SIMD4(Float(target.minX), Float(target.minY), Float(target.width), Float(target.height))
        canvasAndGrid = SIMD4(Float(canvas.width), Float(canvas.height), Float(grid.columns), Float(grid.rows))
        animation = SIMD4(progress, PhotoReassemblyGeometry.scatterRadius(source: source),
            Float(PhotoMotionTokens.reassemblyTravelStart), Float(PhotoMotionTokens.reassemblyColorStart))
        colorAndPadding = SIMD4(Float(PhotoMotionTokens.reassemblyColorEnd), 0, 0, 0)
    }
}
