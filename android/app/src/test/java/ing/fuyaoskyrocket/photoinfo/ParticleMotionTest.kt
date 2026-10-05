package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.motion.ParticleMotion
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoMotionTokens
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ParticleMotionTest {
    @Test fun particleFramesMatchTheSharedAppleAndroidContract() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.take(6)
            .first { File(it, "shared/motion/particle-frames.tsv").isFile }
        File(root, "shared/motion/particle-frames.tsv").readLines().drop(1).filter { it.isNotBlank() }.forEach { line ->
            val values = line.split('\t').map(String::toDouble)
            val actual = ParticleMotion.frame(values[0].toInt(), values[1].toInt(), values[2].toFloat())
            listOf(actual.x, actual.y, actual.scale, actual.alpha).forEachIndexed { index, value ->
                assertEquals(values[index + 3], value.toDouble(), .00001)
            }
        }
    }

    @Test fun particleBudgetHoldsForNarrowWideAndLargeTextRows() {
        listOf(1f to 10000f, 320f to 48f, 480f to 300f, 1200f to 100f, 10000f to 1f).forEach { (width, height) ->
            val grid = ParticleMotion.grid(width, height)
            assertTrue(grid.count in 1..PhotoMotionTokens.maxParticles)
        }
        assertEquals(0, ParticleMotion.grid(0f, 48f).count)
        assertEquals(0, ParticleMotion.grid(Float.NaN, 48f).count)
        assertEquals(0, ParticleMotion.grid(100f, Float.POSITIVE_INFINITY).count)
    }

    @Test fun removalStartsAsTheIntactRowAndFullyFinishes() {
        for (index in 0 until 720) {
            val start = ParticleMotion.frame(index, 36, 0f)
            assertEquals(0f, start.x, 0f)
            assertEquals(0f, start.y, 0f)
            assertEquals(1f, start.alpha, 0f)
            assertEquals(1f, start.scale, 0f)
            assertEquals(0f, ParticleMotion.frame(index, 36, 1f).alpha, .00001f)
        }
    }

    @Test fun gestureDirectionMirrorsTravelWithoutChangingLifetime() {
        for (index in 0 until 720) {
            val forward = ParticleMotion.frame(index, 36, .65f)
            val reverse = ParticleMotion.frame(index, 36, .65f, reverse = true)
            assertEquals(-forward.x, reverse.x, 0f)
            assertEquals(forward.y, reverse.y, 0f)
            assertEquals(forward.alpha, reverse.alpha, 0f)
            assertTrue(forward.alpha in 0f..1f && forward.scale in 0f..1f)
            assertTrue(forward.x.isFinite() && forward.y.isFinite())
        }
    }

    @Test fun rightmostFragmentsWaitForTheRemovalWave() {
        assertTrue(ParticleMotion.frame(0, 10, .1f).x > 0f)
        assertEquals(0f, ParticleMotion.frame(9, 10, .1f).x, 0f)
        assertEquals(ParticleMotion.frame(3, 10, 0f), ParticleMotion.frame(3, 10, Float.NaN))
        assertEquals(ParticleMotion.frame(3, 10, 1f), ParticleMotion.frame(3, 10, 2f))
    }

    @Test fun batchProgressNeverInventsAnUnknownTotalOrExceedsItsBounds() {
        assertEquals(0f, ParticleMotion.progress(1, 0), 0f)
        assertEquals(0f, ParticleMotion.progress(-1, 10), 0f)
        assertEquals(.3f, ParticleMotion.progress(3, 10), .00001f)
        assertEquals(1f, ParticleMotion.progress(11, 10), 0f)
    }
}
