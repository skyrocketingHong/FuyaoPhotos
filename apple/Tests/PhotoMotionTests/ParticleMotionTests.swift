import Testing
import PhotoMotionCore

@Test func batchProgressRepresentsCompletedItemsOnly() {
    #expect(ParticleMotion.progress(completed: 1, total: 0) == 0)
    #expect(ParticleMotion.progress(completed: -1, total: 10) == 0)
    #expect(ParticleMotion.progress(completed: 3, total: 10) == 0.3)
    #expect(ParticleMotion.progress(completed: 11, total: 10) == 1)
}
