import Foundation
import CoreGraphics
import Testing
import PhotoMotionCore

struct PhotoReassemblyCurveTests {
    @Test func bothPlatformsFollowTheSharedHighPrecisionReference() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
        let data = try Data(contentsOf: root.appendingPathComponent("shared/motion/reassembly-progress.json"))
        let fixture = try JSONDecoder().decode(Reference.self, from: data)
        for sample in fixture.samples {
            let actual = PhotoReassemblyGeometry.progress(Float(sample.t))
            #expect(abs(Double(actual.travel) - sample.travel) <= fixture.absoluteTolerance)
            #expect(abs(Double(actual.scatter) - sample.scatter) <= fixture.absoluteTolerance)
            #expect(abs(Double(actual.color) - sample.color) <= fixture.absoluteTolerance)
            #expect(abs(Double(actual.cellScale) - sample.cellScale) <= fixture.absoluteTolerance)
        }
    }

    @Test func denseSamplesNeverReverseTravelColorOrOvershootTheSettleFrame() {
        let terminal = PhotoReassemblyGeometry.progress(1)
        var previous = PhotoReassemblyGeometry.progress(0)
        for index in 1...4000 {
            let t = Float(index) / 4000
            let sample = PhotoReassemblyGeometry.progress(t)
            #expect(sample.travel.isFinite && sample.scatter.isFinite && sample.color.isFinite)
            #expect((0...1).contains(sample.travel) && (0...1).contains(sample.scatter) && (0...1).contains(sample.color))
            #expect((Float(0.45)...1).contains(sample.cellScale))
            #expect(sample.travel >= previous.travel && sample.color >= previous.color)
            if t <= Float(PhotoMotionTokens.reassemblyTravelStart) { #expect(sample.scatter >= previous.scatter) }
            else { #expect(sample.scatter <= previous.scatter) }
            if t >= Float(PhotoMotionTokens.reassemblySettleStart) { #expect(sample == terminal) }
            previous = sample
        }
    }

    @Test func breakupRespondsQuicklyAndGatheringAcceleratesThenSettles() {
        let start = Float(PhotoMotionTokens.reassemblyTravelStart)
        let end = Float(PhotoMotionTokens.reassemblySettleStart)
        let quarterBreakup = PhotoReassemblyGeometry.progress(start * 0.25).scatter
        let halfwayBreakup = PhotoReassemblyGeometry.progress(start * 0.5).scatter
        #expect(quarterBreakup > 0.7)
        #expect(halfwayBreakup - quarterBreakup < quarterBreakup)

        func travel(_ fraction: Float) -> Float {
            PhotoReassemblyGeometry.progress(start + (end - start) * fraction).travel
        }
        #expect(travel(0.25) < 0.2)
        #expect(travel(0.75) > 0.8)
        let firstQuarter = travel(0.25) - travel(0)
        let middleHalf = travel(0.75) - travel(0.25)
        let finalQuarter = travel(1) - travel(0.75)
        #expect(middleHalf > 3 * firstQuarter && middleHalf > 3 * finalQuarter)
        #expect((travel(0.55) - travel(0.45)) / 0.1 > 3)
        for index in 5...35 {
            let t = Float(index) / 100
            #expect(travel(t + 0.01) - 2 * travel(t) + travel(t - 0.01) > 0)
        }
        for index in 65...95 {
            let t = Float(index) / 100
            #expect(travel(t + 0.01) - 2 * travel(t) + travel(t - 0.01) < 0)
        }

        let epsilon: Float = 0.0001
        let beforePeak = PhotoReassemblyGeometry.progress(start - epsilon).scatter
        let afterPeak = PhotoReassemblyGeometry.progress(start + epsilon).scatter
        #expect(abs(1 - beforePeak) / epsilon < 0.02)
        #expect(abs(1 - afterPeak) / epsilon < 0.02)
        #expect((travel(0.001) - travel(0)) / 0.001 < 0.02)
        #expect((travel(1) - travel(0.999)) / 0.001 < 0.02)
        func color(_ fraction: Float) -> Float {
            let start = Float(PhotoMotionTokens.reassemblyColorStart)
            let end = Float(PhotoMotionTokens.reassemblyColorEnd)
            return PhotoReassemblyGeometry.progress(start + (end - start) * fraction).color
        }
        #expect((color(0.001) - color(0)) / 0.001 < 0.02)
        #expect((color(1) - color(0.999)) / 0.001 < 0.02)
    }

    @Test func settledParticlesAndUniformsStayExactUntilNativeHandoff() throws {
        let source = CGRect(x: -12, y: 8, width: 1023, height: 711)
        let target = CGRect(x: 24, y: 70, width: 358, height: 228)
        let grid = try #require(PhotoReassemblyGeometry.grid(source: source, target: target))
        for index in [0, grid.columns / 2, grid.count / 2, grid.count - 1] {
            let final = try #require(PhotoReassemblyGeometry.particle(index: index, grid: grid,
                source: source, target: target, progress: 1))
            for t: Float in [Float(PhotoMotionTokens.reassemblySettleStart), 0.95, 0.999, 1] {
                let held = try #require(PhotoReassemblyGeometry.particle(index: index, grid: grid,
                    source: source, target: target, progress: t))
                #expect(held == final)
                let uniforms = PhotoReassemblyUniforms(source: source, target: target,
                    canvas: CGSize(width: 1200, height: 900), grid: grid, progress: t)
                #expect(uniforms.animation.x == 1 && uniforms.animation.z == 0 && uniforms.animation.w == 1)
                #expect(uniforms.colorAndPadding.x == 1)
            }
        }
    }

    private struct Reference: Decodable {
        let absoluteTolerance: Double
        let samples: [Sample]
    }

    private struct Sample: Decodable {
        let t: Double
        let travel: Double
        let scatter: Double
        let color: Double
        let cellScale: Double
    }
}
