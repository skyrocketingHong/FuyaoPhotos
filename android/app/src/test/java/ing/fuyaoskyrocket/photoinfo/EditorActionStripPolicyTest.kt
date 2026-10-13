package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.layout.EditorActionStripPolicy
import org.junit.Assert.*
import org.junit.Test

class EditorActionStripPolicyTest {
    @Test fun narrowPhotoPaneKeepsNativeTargetsInsideTheActionWidth() {
        val narrow = EditorActionStripPolicy.calculate(240f, 5)
        assertEquals(1, narrow.inlineToolCount)
        assertTrue(narrow.hasOverflow)
        assertFalse(narrow.scrolls)
        assertTrue(narrow.contentWidth <= 240f)

        val smaller = EditorActionStripPolicy.calculate(180f, 5)
        assertEquals(0, smaller.inlineToolCount)
        assertTrue(smaller.hasOverflow)
        assertFalse(smaller.scrolls)
        assertTrue(smaller.contentWidth <= 180f)
    }

    @Test fun widePaneExposesAllToolsAndTinyPaneKeepsPrimaryActionsReachable() {
        val wide = EditorActionStripPolicy.calculate(440f, 5)
        assertEquals(5, wide.inlineToolCount)
        assertFalse(wide.hasOverflow)
        assertFalse(wide.scrolls)

        val tiny = EditorActionStripPolicy.calculate(140f, 5)
        assertEquals(0, tiny.inlineToolCount)
        assertTrue(tiny.hasOverflow)
        assertTrue(tiny.scrolls)
        assertEquals(160f, tiny.contentWidth, 0f)
    }
}
