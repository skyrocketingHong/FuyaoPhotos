package ing.fuyaoskyrocket.photoinfo.domain.motion

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

object ParticleMotion {
    data class Grid(val columns: Int, val rows: Int) {
        val count get() = columns * rows
    }
    data class Frame(val x: Float, val y: Float, val scale: Float, val alpha: Float)

    fun grid(width: Float, height: Float): Grid {
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return Grid(0, 0)
        val cell = max(PhotoMotionTokens.minimumParticleSize.toDouble(),
            sqrt(width.toDouble() * height / PhotoMotionTokens.maxParticles))
        val columns = floor(width / cell).toInt().coerceIn(1, PhotoMotionTokens.maxParticles)
        val rows = floor(height / cell).toInt().coerceIn(1, PhotoMotionTokens.maxParticles / columns)
        return Grid(columns, rows)
    }

    fun frame(index: Int, columns: Int, progress: Float, reverse: Boolean = false): Frame {
        val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
        val column = index.coerceAtLeast(0) % columns.coerceAtLeast(1)
        val delay = column.toFloat() / max(1, columns - 1) * PhotoMotionTokens.particleStagger
        val age = ((p - delay) / (1f - delay)).coerceIn(0f, 1f)
        val fade = ((age - .2f) / .8f).coerceIn(0f, 1f)
        val sign = if (reverse) -1f else 1f
        return Frame(
            sign * PhotoMotionTokens.particleTravel * (.3f + random(index, 1) * .7f) * age,
            PhotoMotionTokens.particleLift * ((random(index, 2) - .5f) * age - age * age),
            1f - PhotoMotionTokens.particleShrink * age,
            1f - fade * fade * (3f - 2f * fade),
        )
    }

    private fun random(index: Int, channel: Int): Float {
        val seed = index.toLong().coerceAtLeast(0) * 3 + channel
        return ((seed * 1_103_515_245L + 12_345L) and 0x7fffffff).toFloat() / 2_147_483_647f
    }

    fun progress(completed: Int, total: Int): Float =
        if (total > 0) completed.coerceIn(0, total).toFloat() / total else 0f
}
