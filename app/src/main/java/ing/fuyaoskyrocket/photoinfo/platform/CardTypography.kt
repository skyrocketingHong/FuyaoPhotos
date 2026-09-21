package ing.fuyaoskyrocket.photoinfo.platform

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import ing.fuyaoskyrocket.photoinfo.domain.layout.CardLayout
import ing.fuyaoskyrocket.photoinfo.domain.typography.CardFontRuns
import kotlin.math.ceil

/** Immutable font selection, captured together for each preview/export operation. */
data class CardTypography(
    val letters: Typeface,
    val numbers: Typeface = letters,
    val letterFeatures: String? = null,
    val mixedDigits: Boolean = false,
) {
    companion object {
        fun uniform(typeface: Typeface) = CardTypography(typeface)
    }
}

/** One shaping path owns widths, glyph placement and the HDR coverage mask. */
class CardTextRenderer(private val typography: CardTypography, fontSize: Float) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
        typeface = typography.letters
        fontFeatureSettings = typography.letterFeatures
        textSize = fontSize
    }
    private val letterMetrics = paint.fontMetrics
    private val numberMetrics = TextPaint(paint).apply {
        typeface = typography.numbers
        fontFeatureSettings = null
    }.fontMetrics
    // Keep the same baseline on rows with and without digits.
    private val ascent = minOf(letterMetrics.ascent, numberMetrics.ascent)
    private val descent = maxOf(letterMetrics.descent, numberMetrics.descent)

    internal fun styledText(text: String): CharSequence {
        if (!typography.mixedDigits) return text
        return SpannableString(text).apply {
            CardFontRuns.digits(text).forEach { range ->
                setSpan(DigitTypefaceSpan(typography.numbers), range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    fun measure(text: String): Float = Layout.getDesiredWidth(styledText(text), paint)

    fun draw(canvas: Canvas, text: String, x: Float, top: Float, lineHeight: Float, color: Int) {
        if (text.isEmpty()) return
        paint.color = color
        val styled = styledText(text)
        // Two pixels absorb float-to-integer width rounding without clipping a final glyph.
        val width = ceil(Layout.getDesiredWidth(styled, paint)).toInt().coerceAtLeast(1) + 2
        val line = StaticLayout.Builder.obtain(styled, 0, styled.length, paint, width)
            .setIncludePad(false)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
        check(line.lineCount == 1 && line.getLineEnd(0) == styled.length) { "Card line did not fit its measured width" }
        val baseline = (lineHeight - (descent - ascent)) / 2f - ascent
        val save = canvas.save()
        try {
            // Keep even RTL content on the card's common physical left edge.
            canvas.translate(x - line.getLineLeft(0), top + baseline - line.getLineBaseline(0))
            line.draw(canvas)
        } finally { canvas.restoreToCount(save) }
    }

    fun drawLines(canvas: Canvas, layout: CardLayout, mask: Boolean = false) {
        layout.lines.forEach { line ->
            draw(canvas, line.text, line.x, line.top, layout.lineHeight,
                if (mask || !line.accent) Color.WHITE else Color.rgb(255, 218, 69))
        }
    }

    private class DigitTypefaceSpan(private val font: Typeface) : MetricAffectingSpan() {
        override fun updateMeasureState(textPaint: TextPaint) = apply(textPaint)
        override fun updateDrawState(textPaint: TextPaint) = apply(textPaint)
        private fun apply(paint: TextPaint) {
            paint.typeface = font
            paint.fontFeatureSettings = null
        }
    }
}
