package ing.fuyaoskyrocket.photoinfo.domain.motion

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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
        if (t == 0f) return Progress(0f, 0f, 0f, 1f)
        if (t >= PhotoMotionTokens.reassemblySettleStart) return Progress(1f, 0f, 1f, 1f)
        val travel = travelCurve(interval(t, PhotoMotionTokens.reassemblyTravelStart, PhotoMotionTokens.reassemblySettleStart))
        val scatter = if (t < PhotoMotionTokens.reassemblyTravelStart) {
            cubicBezier(interval(t, 0f, PhotoMotionTokens.reassemblyTravelStart),
                PhotoMotionTokens.reassemblyScatterCurveX1, PhotoMotionTokens.reassemblyScatterCurveY1,
                PhotoMotionTokens.reassemblyScatterCurveX2, PhotoMotionTokens.reassemblyScatterCurveY2)
        } else 1.0 - travel
        return Progress(
            travel = travel.toFloat(),
            scatter = scatter.toFloat(),
            color = travelCurve(interval(t, PhotoMotionTokens.reassemblyColorStart, PhotoMotionTokens.reassemblyColorEnd)).toFloat(),
            cellScale = (1.0 - .55 * scatter).toFloat(),
        )
    }

    private fun interval(value: Float, start: Float, end: Float): Double =
        ((value.toDouble() - start) / (end.toDouble() - start)).coerceIn(0.0, 1.0)

    private fun travelCurve(value: Double): Double = cubicBezier(value,
        PhotoMotionTokens.reassemblyTravelCurveX1, PhotoMotionTokens.reassemblyTravelCurveY1,
        PhotoMotionTokens.reassemblyTravelCurveX2, PhotoMotionTokens.reassemblyTravelCurveY2)

    private fun cubicBezier(value: Double, x1: Float, y1: Float, x2: Float, y2: Float): Double {
        if (value <= 0.0 || value >= 1.0) return value.coerceIn(0.0, 1.0)
        fun coordinate(t: Double, first: Float, second: Float): Double {
            val inverse = 1.0 - t
            return 3.0 * inverse * inverse * t * first + 3.0 * inverse * t * t * second + t * t * t
        }
        var low = 0.0
        var high = 1.0
        // Solve the time coordinate before evaluating the curve's displacement.
        repeat(28) {
            val parameter = (low + high) / 2.0
            if (coordinate(parameter, x1, x2) < value) low = parameter else high = parameter
        }
        return coordinate((low + high) / 2.0, y1, y2)
    }
}
