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

    public struct Progress: Equatable, Sendable {
        public let travel: Float
        public let scatter: Float
        public let color: Float
        public let cellScale: Float
    }

    public static func progress(_ value: Float) -> Progress {
        precondition(value.isFinite)
        let t = min(1, max(0, value))
        if t == 0 { return Progress(travel: 0, scatter: 0, color: 0, cellScale: 1) }
        if t >= Float(PhotoMotionTokens.reassemblySettleStart) {
            return Progress(travel: 1, scatter: 0, color: 1, cellScale: 1)
        }
        let travel = travelCurve(interval(t, Float(PhotoMotionTokens.reassemblyTravelStart), Float(PhotoMotionTokens.reassemblySettleStart)))
        let scatter: Double
        if t < Float(PhotoMotionTokens.reassemblyTravelStart) {
            scatter = cubicBezier(interval(t, 0, Float(PhotoMotionTokens.reassemblyTravelStart)),
                Float(PhotoMotionTokens.reassemblyScatterCurveX1), Float(PhotoMotionTokens.reassemblyScatterCurveY1),
                Float(PhotoMotionTokens.reassemblyScatterCurveX2), Float(PhotoMotionTokens.reassemblyScatterCurveY2))
        } else { scatter = 1 - travel }
        return Progress(travel: Float(travel), scatter: Float(scatter),
            color: Float(travelCurve(interval(t, Float(PhotoMotionTokens.reassemblyColorStart), Float(PhotoMotionTokens.reassemblyColorEnd)))),
            cellScale: Float(1 - 0.55 * scatter))
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
        let weights = Self.progress(progress)
        let resolution = SIMD2<Float>(Float(grid.columns), Float(grid.rows))
        let uv = (SIMD2<Float>(Float(index % grid.columns), Float(index / grid.columns)) + SIMD2(repeating: 0.5)) / resolution
        let sourceSize = SIMD2<Float>(Float(source.width), Float(source.height))
        let targetSize = SIMD2<Float>(Float(target.width), Float(target.height))
        let sourceCenter = SIMD2<Float>(Float(source.minX), Float(source.minY)) + uv * sourceSize
        let targetCenter = SIMD2<Float>(Float(target.minX), Float(target.minY)) + uv * targetSize
        let center: SIMD2<Float>
        let size: SIMD2<Float>
        if weights.travel == 1 {
            center = targetCenter
            size = targetSize / resolution
        } else if weights.scatter == 0 && weights.travel == 0 {
            center = sourceCenter
            size = sourceSize / resolution
        } else {
            center = sourceCenter + (targetCenter - sourceCenter) * weights.travel
                + offset(index: index) * scatterRadius(source: source) * weights.scatter
            size = (sourceSize + (targetSize - sourceSize) * weights.travel) / resolution * weights.cellScale
        }
        return Particle(center: center, size: size, colorFraction: weights.color)
    }

    private static func interval(_ value: Float, _ start: Float, _ end: Float) -> Double {
        min(1, max(0, (Double(value) - Double(start)) / (Double(end) - Double(start))))
    }

    private static func travelCurve(_ value: Double) -> Double {
        cubicBezier(value, Float(PhotoMotionTokens.reassemblyTravelCurveX1), Float(PhotoMotionTokens.reassemblyTravelCurveY1),
            Float(PhotoMotionTokens.reassemblyTravelCurveX2), Float(PhotoMotionTokens.reassemblyTravelCurveY2))
    }

    private static func cubicBezier(_ value: Double, _ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float) -> Double {
        if value <= 0 || value >= 1 { return min(1, max(0, value)) }
        func coordinate(_ t: Double, _ first: Float, _ second: Float) -> Double {
            let inverse = 1 - t
            return 3 * inverse * inverse * t * Double(first) + 3 * inverse * t * t * Double(second) + t * t * t
        }
        var low = 0.0
        var high = 1.0
        // Solve the time coordinate before evaluating the curve's displacement.
        for _ in 0..<28 {
            let parameter = (low + high) / 2
            if coordinate(parameter, x1, x2) < value { low = parameter }
            else { high = parameter }
        }
        return coordinate((low + high) / 2, y1, y2)
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
        let weights = PhotoReassemblyGeometry.progress(progress)
        animation = SIMD4(weights.travel, PhotoReassemblyGeometry.scatterRadius(source: source), weights.scatter, weights.cellScale)
        colorAndPadding = SIMD4(weights.color, 0, 0, 0)
    }
}
