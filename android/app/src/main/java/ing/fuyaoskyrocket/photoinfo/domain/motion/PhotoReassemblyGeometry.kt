package ing.fuyaoskyrocket.photoinfo.domain.motion

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object PhotoReassemblyGeometry {
    data class Grid(val columns: Int, val rows: Int) {
        val count: Int get() = columns * rows
    }

    data class Progress(val travel: Float, val scatter: Float, val color: Float, val cellScale: Float)

    fun grid(sourceWidth: Float, sourceHeight: Float, targetWidth: Float, targetHeight: Float, density: Float): Grid {
        require(listOf(sourceWidth, sourceHeight, targetWidth, targetHeight, density).all { it.isFinite() && it > 0f })
        val limit = PhotoMotionTokens.reassemblyMaxParticles
        val cell = PhotoMotionTokens.reassemblyCellSize * density
        var columns = ceil(max(sourceWidth, targetWidth) / cell).toInt().coerceIn(1, limit)
        var rows = ceil(max(sourceHeight, targetHeight) / cell).toInt().coerceIn(1, limit)
        val count = columns.toLong() * rows
        if (count > limit) {
            val scale = sqrt(limit.toDouble() / count)
            columns = floor(columns * scale).toInt().coerceAtLeast(1)
            rows = floor(rows * scale).toInt().coerceAtLeast(1)
        }
        rows = rows.coerceAtMost(limit / columns)
        return Grid(columns, rows)
    }

    fun scatterRadius(sourceWidth: Float, sourceHeight: Float, density: Float): Float {
        require(listOf(sourceWidth, sourceHeight, density).all { it.isFinite() && it > 0f })
        return min(64f * density, max(16f * density, min(sourceWidth, sourceHeight) * PhotoMotionTokens.reassemblyScatter))
    }

    fun progress(value: Float): Progress {
        require(value.isFinite())
        val t = value.coerceIn(0f, 1f)
        val scatter = if (t == 0f || t == 1f) 0f else sin(Math.PI * t).toFloat()
        return Progress(
            travel = smoothstep(PhotoMotionTokens.reassemblyTravelStart, 1f, t),
            scatter = scatter,
            color = smoothstep(PhotoMotionTokens.reassemblyColorStart, PhotoMotionTokens.reassemblyColorEnd, t),
            cellScale = 1f - .55f * scatter,
        )
    }

    private fun smoothstep(start: Float, end: Float, value: Float): Float {
        val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
