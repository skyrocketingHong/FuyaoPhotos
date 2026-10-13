package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoMotionTokens
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoReassemblyGeometry
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoReassemblyLifecycle
import org.junit.Assert.*
import org.junit.Test

class PhotoReassemblyTest {
    @Test fun endpointsUseTheExactSourceAndTargetWithoutResidualScatter() {
        assertEquals(PhotoReassemblyGeometry.Progress(0f, 0f, 0f, 1f), PhotoReassemblyGeometry.progress(0f))
        assertEquals(PhotoReassemblyGeometry.Progress(1f, 0f, 1f, 1f), PhotoReassemblyGeometry.progress(1f))
        val middle = PhotoReassemblyGeometry.progress(.5f)
        assertTrue(middle.travel in 0f..1f)
        assertEquals(1f, middle.scatter, .00001f)
        assertEquals(.45f, middle.cellScale, .00001f)
        assertEquals(0f, PhotoReassemblyGeometry.progress(PhotoMotionTokens.reassemblyTravelStart).travel, 0f)
    }

    @Test fun gridsStayBoundedForDifferentPhotoAndCardAspectRatios() {
        for (sizes in listOf(floatArrayOf(1080f, 810f, 960f, 700f), floatArrayOf(9000f, 100f, 1f, 12000f),
            floatArrayOf(1f, 1f, 1f, 1f), floatArrayOf(8000f, 6000f, 4000f, 3000f))) {
            val grid = PhotoReassemblyGeometry.grid(sizes[0], sizes[1], sizes[2], sizes[3], 3f)
            assertTrue(grid.columns > 0 && grid.rows > 0)
            assertTrue(grid.count <= PhotoMotionTokens.reassemblyMaxParticles)
        }
        assertEquals(48f, PhotoReassemblyGeometry.scatterRadius(10f, 10f, 3f), 0f)
        assertEquals(192f, PhotoReassemblyGeometry.scatterRadius(10000f, 10000f, 3f), 0f)
    }

    @Test fun oldCaptureAndCompletionCannotReplaceANewerClose() {
        val lifecycle = PhotoReassemblyLifecycle()
        val old = lifecycle.begin("metadata-photo", 100)
        lifecycle.cancel()
        val current = lifecycle.begin("colors-photo", 120)
        assertFalse(lifecycle.acceptsTarget(old.generation, old.feature, 130))
        assertFalse(lifecycle.finish(old.generation))
        assertEquals(current, lifecycle.current)
        assertTrue(lifecycle.acceptsTarget(current.generation, current.feature, 150))
        assertFalse(lifecycle.acceptsTarget(current.generation, current.feature, 160))
        assertTrue(lifecycle.finish(current.generation))
        assertNull(lifecycle.current)
    }

    @Test fun waitingAndRenderingHaveIndependentDeadlines() {
        val lifecycle = PhotoReassemblyLifecycle()
        val missing = lifecycle.begin("metadata-photo", 0)
        assertNull(lifecycle.expiry(missing.targetDeadline - 1))
        assertEquals(PhotoReassemblyLifecycle.Expiry.TARGET_WAIT, lifecycle.expiry(missing.targetDeadline))
        assertFalse(lifecycle.acceptsTarget(missing.generation, missing.feature, missing.targetDeadline))
        val ready = lifecycle.begin("cards-photo", 1000)
        assertTrue(lifecycle.acceptsTarget(ready.generation, ready.feature, 1001))
        assertNull(lifecycle.expiry(ready.targetDeadline + 1))
        assertEquals(PhotoReassemblyLifecycle.Expiry.LIFETIME, lifecycle.expiry(ready.deadline))
    }
}
