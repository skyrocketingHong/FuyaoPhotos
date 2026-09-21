package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.typography.CardFontRuns
import org.junit.Assert.*
import org.junit.Test

class CardFontRunsTest {
    private fun digits(text: String) = CardFontRuns.monospacedDigits(text).map { text.substring(it.start,it.end) }

    @Test fun selectedNumbersSwitchFontsAndPunctuationKeepsItsProportionalSpacing() {
        val text="FOCAL LENGTH: 75 MM (3.2X)"
        assertEquals(listOf("75","3","2"),digits(text))
        val selected=CardFontRuns.monospacedDigits(text).flatMap { it.start until it.end }.toSet()
        text.forEachIndexed { index,c -> assertEquals(c in '0'..'9' && c != '1',index in selected) }
        assertEquals(listOf("2","48","400"),digits("1/121 · 1.48 · ISO: 400"))
    }
    @Test fun proportionalOnesSplitRunsInModelNamesExposureAndZoom() {
        val text="iPHONE 18 PRO 1/121 (1X) 1.48 101 111"
        assertEquals(listOf("8","2","48","0"),digits(text))
        CardFontRuns.monospacedDigits(text).forEach { range ->
            assertFalse(text.substring(range.start,range.end).contains('1'))
        }
        assertTrue(CardFontRuns.monospacedDigits("111").isEmpty())
    }
    @Test fun utf16RangesDoNotSplitEmojiOrChangeLettersWithCombiningMarks() {
        val text="📷 CAFE\u0301 東京 27 ULTRA"
        val range=CardFontRuns.monospacedDigits(text).single()
        assertEquals("27",text.substring(range.start,range.end))
        assertEquals("📷 CAFE\u0301 東京 ",text.substring(0,range.start))
    }
    @Test fun digitGraphemesStayWholeWhenTheyCarryMarksOrEmojiSelectors() {
        assertEquals(listOf("3","45"),digits("1️⃣ 2️⃣ 2\u0301 3 45"))
        assertTrue(CardFontRuns.monospacedDigits("A\u200D2").isEmpty())
    }
    @Test fun emptyTextAndNonAsciiDigitsStayInTheirOriginalScript() {
        assertTrue(CardFontRuns.monospacedDigits("").isEmpty())
        assertTrue(CardFontRuns.monospacedDigits("ISO １２３ ١٢٣").isEmpty())
        assertEquals(listOf("00"),digits("100"))
    }
}
