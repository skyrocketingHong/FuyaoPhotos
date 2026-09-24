package ing.fuyaoskyrocket.photoinfo.domain.typography

/** Separate standalone digit runs without splitting combining marks or keycap emoji. */
object CardFontRuns {
    data class Range(val start: Int, val end: Int)

    private fun isStandaloneDigit(text: String, index: Int): Boolean {
        if (index >= text.length || text[index] !in '0'..'9') return false
        if (index > 0 && text[index - 1] == '\u200D') return false
        if (index + 1 == text.length) return true
        val next = text.codePointAt(index + 1)
        if (next == 0x200D) return false
        // Keep combining accents, variation selectors and keycap emoji in one font run.
        return when (Character.getType(next)) {
            Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt() -> false
            else -> true
        }
    }

    fun monospacedDigits(text: String): List<Range> = ranges(text) { it != '1' }
    fun proportionalOnes(text: String): List<Range> = ranges(text) { it == '1' }

    private inline fun ranges(text: String, selected: (Char) -> Boolean): List<Range> {
        val ranges = mutableListOf<Range>()
        var start = -1
        for (index in 0..text.length) {
            val digit = isStandaloneDigit(text, index) && selected(text[index])
            if (digit && start < 0) start = index
            if (!digit && start >= 0) {
                ranges += Range(start, index)
                start = -1
            }
        }
        return ranges
    }
}
