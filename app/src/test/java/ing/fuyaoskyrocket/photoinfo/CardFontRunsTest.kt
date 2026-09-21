package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.typography.CardFontRuns
import org.junit.Assert.*
import org.junit.Test

class CardFontRunsTest {
    private fun digits(text: String) = CardFontRuns.digits(text).map { text.substring(it.start,it.end) }

    @Test fun onlyNumbersSwitchFontsAndPunctuationKeepsItsProportionalSpacing() {
        val text="FOCAL LENGTH: 75 MM (3.2X)"
        assertEquals(listOf("75","3","2"),digits(text))
        val selected=CardFontRuns.digits(text).flatMap { it.start until it.end }.toSet()
        text.forEachIndexed { index,c -> assertEquals(c in '0'..'9',index in selected) }
        assertEquals(listOf("1","121","1","48","400"),digits("1/121 · 1.48 · ISO: 400"))
    }
    @Test fun utf16RangesDoNotSplitEmojiOrChangeLettersWithCombiningMarks() {
        val text="📷 CAFE\u0301 東京 17 ULTRA"
        val range=CardFontRuns.digits(text).single()
        assertEquals("17",text.substring(range.start,range.end))
        assertEquals("📷 CAFE\u0301 東京 ",text.substring(0,range.start))
    }
    @Test fun digitGraphemesStayWholeWhenTheyCarryMarksOrEmojiSelectors() {
        assertEquals(listOf("3","45"),digits("1️⃣ 2\u0301 3 45"))
        assertTrue(CardFontRuns.digits("A\u200D1").isEmpty())
    }
    @Test fun emptyTextAndNonAsciiDigitsStayInTheirOriginalScript() {
        assertTrue(CardFontRuns.digits("").isEmpty())
        assertTrue(CardFontRuns.digits("ISO １２３ ١٢٣").isEmpty())
        assertEquals(listOf("100"),digits("100"))
    }
}
