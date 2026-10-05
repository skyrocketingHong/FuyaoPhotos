import Testing
import Foundation
import PhotoMotionCore

@Test func particleFramesMatchTheSharedAppleAndroidContract() throws {
    let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent()
    let fixture = try String(contentsOf: root.appendingPathComponent("shared/motion/particle-frames.tsv"), encoding: .utf8)
    for line in fixture.split(separator: "\n").dropFirst() {
        let values = try line.split(separator: "\t").map { try #require(Double($0)) }
        let actual = ParticleMotion.frame(index: Int(values[0]), columns: Int(values[1]), progress: values[2])
        for (index, value) in [actual.x, actual.y, actual.scale, actual.alpha].enumerated() {
            #expect(abs(value - values[index + 3]) < 0.00001)
        }
    }
}

@Test func particleBudgetHoldsAcrossRowShapes() {
    for (width, height) in [(1.0, 10000.0), (320.0, 48.0), (480.0, 300.0), (1200.0, 100.0), (10000.0, 1.0)] {
        let grid = ParticleMotion.grid(width: width, height: height)
        #expect((1...PhotoMotionTokens.maxParticles).contains(grid.count))
    }
    #expect(ParticleMotion.grid(width: 0, height: 48).count == 0)
    #expect(ParticleMotion.grid(width: .nan, height: 48).count == 0)
    #expect(ParticleMotion.grid(width: 100, height: .infinity).count == 0)
}

@Test func removedRowsStartIntactAndFinishTransparent() {
    for index in 0..<720 {
        let start = ParticleMotion.frame(index: index, columns: 36, progress: 0)
        #expect(start.x == 0 && start.y == 0 && start.alpha == 1 && start.scale == 1)
        #expect(abs(ParticleMotion.frame(index: index, columns: 36, progress: 1).alpha) < 0.00001)
    }
}

@Test func reverseDirectionPreservesLifetime() {
    for index in 0..<720 {
        let forward = ParticleMotion.frame(index: index, columns: 36, progress: 0.65)
        let reverse = ParticleMotion.frame(index: index, columns: 36, progress: 0.65, reverse: true)
        #expect(forward.x == -reverse.x && forward.y == reverse.y && forward.alpha == reverse.alpha)
        #expect((0...1).contains(forward.alpha) && (0...1).contains(forward.scale))
        #expect(forward.x.isFinite && forward.y.isFinite)
    }
}

@Test func removalWaveAndInvalidProgressAreBounded() {
    #expect(ParticleMotion.frame(index: 0, columns: 10, progress: 0.1).x > 0)
    #expect(ParticleMotion.frame(index: 9, columns: 10, progress: 0.1).x == 0)
    #expect(ParticleMotion.frame(index: 3, columns: 10, progress: .nan) == ParticleMotion.frame(index: 3, columns: 10, progress: 0))
    #expect(ParticleMotion.frame(index: 3, columns: 10, progress: 2) == ParticleMotion.frame(index: 3, columns: 10, progress: 1))
}

@Test func batchProgressRepresentsCompletedItemsOnly() {
    #expect(ParticleMotion.progress(completed: 1, total: 0) == 0)
    #expect(ParticleMotion.progress(completed: -1, total: 10) == 0)
    #expect(ParticleMotion.progress(completed: 3, total: 10) == 0.3)
    #expect(ParticleMotion.progress(completed: 11, total: 10) == 1)
}
