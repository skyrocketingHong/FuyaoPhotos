package ing.fuyaoskyrocket.photoinfo.domain.motion

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Nagram ThanosEffect.calcParticlesGrid; the rendering shader is kept verbatim. */
object TelegramDustGrid {
    data class Grid(val columns: Int, val rows: Int, val size: Float) {
        val count: Int get() = columns * rows
    }

    fun calculate(width: Int, height: Int, density: Float, budget: Int): Grid {
        require(width > 0 && height > 0 && density.isFinite() && density > 0 && budget >= 10)
        val pixel = max(density * .4f, 1f)
        val count = (width.toDouble() * height / (pixel * pixel)).coerceIn(10.0, budget.toDouble()).toInt()
        val aspect = width.toDouble() / height
        var rows = sqrt(count / aspect).roundToInt().coerceAtLeast(1)
        var columns = (count.toDouble() / rows).roundToInt().coerceAtLeast(1)
        while (columns * rows < count) {
            if (columns.toDouble() / rows < aspect) columns++ else rows++
        }
        return Grid(columns, rows, max(width.toFloat() / columns, height.toFloat() / rows))
    }
}
