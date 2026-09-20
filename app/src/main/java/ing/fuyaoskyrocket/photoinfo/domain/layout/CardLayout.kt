package ing.fuyaoskyrocket.photoinfo.domain.layout

import ing.fuyaoskyrocket.photoinfo.domain.model.CardStyle
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoInfo
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class CardOverflowException : IllegalArgumentException("Card content exceeds the image height")

data class CardBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right get() = left + width
    val bottom get() = top + height
}
data class CardLine(val text: String, val accent: Boolean, val x: Float, val top: Float)
data class CardLayout(
    val box: CardBox,
    val lines: List<CardLine>,
    val fontSize: Float,
    val lineHeight: Float,
    val radius: Float,
    val blurRadius: Float,
)

/** All typography is measured in the 859px reference short-edge space, not Android dp/sp. */
object CardLayoutEngine {
    const val REFERENCE_SHORT_EDGE = 859f
    const val WIDTH = 215f
    const val MIN_HEIGHT = 168f
    const val PADDING = 19f
    const val MIN_VERTICAL_PADDING = 18f
    const val FONT_SIZE = 10.5f
    const val LINE_HEIGHT = 12.5f
    const val GROUP_GAP = 7f

    fun layout(
        width: Int,
        height: Int,
        info: PhotoInfo,
        style: CardStyle,
        measureReferenceText: (String) -> Float,
    ): CardLayout? {
        require(width > 0 && height > 0)
        val rows = info.displayRows()
        if (rows.isEmpty()) return null
        val s = style.sanitized()
        val base = min(width, height) / REFERENCE_SHORT_EDGE
        val unit = base * s.scale
        val referenceLineHeight = LINE_HEIGHT * s.textScale
        val wrapped = rows.flatMap { row ->
            wrap(row.text, WIDTH - 2 * PADDING) { measureReferenceText(it) * s.textScale }
                .map { it to row.accent }
        }
        val hasBoth = wrapped.any { it.second } && wrapped.any { !it.second }
        val contentHeight = wrapped.size * referenceLineHeight + if (hasBoth) GROUP_GAP else 0f
        val cardHeight = max(MIN_HEIGHT, contentHeight + 2 * MIN_VERTICAL_PADDING) * unit
        val cardWidth = WIDTH * unit
        if (cardHeight > height || cardWidth > width) throw CardOverflowException()
        // Insets are independent from the card scale and clamped to keep all pixels inside the photo.
        val rightInset = (s.rightInset * base).coerceIn(0f, width - cardWidth)
        val bottomInset = (s.bottomInset * base).coerceIn(0f, height - cardHeight)
        val box = CardBox(width - rightInset - cardWidth, height - bottomInset - cardHeight,
            cardWidth, cardHeight)
        var y = box.top + (cardHeight - contentHeight * unit) / 2f
        var previousAccent = wrapped.first().second
        val lines = wrapped.map { (text, accent) ->
            if (previousAccent && !accent) y += GROUP_GAP * unit
            val line = CardLine(text, accent, box.left + PADDING * unit, y)
            y += referenceLineHeight * unit
            previousAccent = accent
            line
        }
        return CardLayout(box, lines, FONT_SIZE * unit * s.textScale, referenceLineHeight * unit,
            min(s.cornerRadius * unit, min(cardWidth, cardHeight) / 2), s.blur * unit)
    }

    /** Word wrapping with grapheme fallback for long identifiers and CJK; never ellipsizes. */
    fun wrap(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        require(maxWidth > 0)
        val result = mutableListOf<String>()
        for (paragraph in text.replace("\r", "").split('\n')) {
            var remaining = paragraph.trim()
            if (remaining.isEmpty()) { result += ""; continue }
            while (remaining.isNotEmpty()) {
                if (measure(remaining) <= maxWidth) { result += remaining; break }
                val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
                iterator.setText(remaining)
                var fit = 0
                var boundary = iterator.first()
                while (boundary != BreakIterator.DONE) {
                    if (measure(remaining.substring(0, boundary)) > maxWidth) break
                    fit = boundary
                    boundary = iterator.next()
                }
                // A custom font glyph wider than the entire card must not loop forever.
                if (fit == 0) throw CardOverflowException()
                var split = fit
                for (index in fit - 1 downTo 1) {
                    if (remaining[index].isWhitespace()) { split = index; break }
                }
                result += remaining.substring(0, split).trimEnd()
                remaining = remaining.substring(split).trimStart()
            }
        }
        return result
    }
}
