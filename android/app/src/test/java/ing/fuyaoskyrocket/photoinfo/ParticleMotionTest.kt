package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.motion.ParticleMotion
import ing.fuyaoskyrocket.photoinfo.domain.motion.TelegramDustGrid
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ParticleMotionTest {
    @Test fun shadersMatchThePinnedNagramOriginals() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.take(6)
            .first { File(it, "android/app/src/main/res/raw/telegram_thanos_vertex.glsl").isFile }
        val shaders = mapOf(
            "vertex" to "8602253347b52f544b0968eac9ad06b07a36118e52d0df51dbb2114b5c50043d",
            "fragment" to "ba663535302f3cedf49e07ed3c2cfbee9b48459db742d8bbc3ea558af4a5e03a",
        )
        shaders.forEach { (name, expected) ->
            val bytes = File(root, "android/app/src/main/res/raw/telegram_thanos_$name.glsl").readBytes()
            assertEquals(expected, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        }
    }

    @Test fun originalGridKeepsFineParticlesAcrossPhotoAndFormAspectRatios() {
        for ((width, height) in listOf(1080 to 810, 320 to 96, 2560 to 1600, 100 to 2000)) {
            val grid = TelegramDustGrid.calculate(width, height, 3f, 60000)
            val expectedMinimum = (width.toDouble() * height / (1.2 * 1.2)).toInt().coerceIn(10, 60000)
            assertTrue(grid.columns > 0 && grid.rows > 0)
            assertTrue(grid.count >= expectedMinimum - 1)
            assertTrue(grid.count <= expectedMinimum + grid.columns + grid.rows)
            assertTrue(grid.size.isFinite() && grid.size > 0)
        }
    }

    @Test fun batchProgressNeverInventsAnUnknownTotalOrExceedsItsBounds() {
        assertEquals(0f, ParticleMotion.progress(1, 0), 0f)
        assertEquals(0f, ParticleMotion.progress(-1, 10), 0f)
        assertEquals(.3f, ParticleMotion.progress(3, 10), .00001f)
        assertEquals(1f, ParticleMotion.progress(11, 10), 0f)
    }
}
