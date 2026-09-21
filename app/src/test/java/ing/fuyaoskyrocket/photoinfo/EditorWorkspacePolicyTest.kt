package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.layout.EditorWorkspacePolicy
import org.junit.Assert.*
import org.junit.Test

class EditorWorkspacePolicyTest {
    @Test fun portraitKeepsUsableControlsAndLandscapeUsesBothPanes() {
        val portrait = EditorWorkspacePolicy.calculate(600f, 760f, 1f, false)
        assertFalse(portrait.sideBySide)
        assertTrue(760f - portrait.previewHeight >= 180f)
        assertTrue(EditorWorkspacePolicy.calculate(760f, 340f, 1f, false).sideBySide)
    }
    @Test fun keyboardPrioritizesFocusedFieldsInShortWindows() {
        val compact = EditorWorkspacePolicy.calculate(393f, 220f, 1f, true)
        assertEquals(0f, compact.previewHeight, 0f)
        assertTrue(EditorWorkspacePolicy.calculate(393f, 400f, 1f, true).previewHeight <= 112f)
    }
    @Test fun largeTextDoesNotLeaveAnUndersizedSidePane() {
        assertFalse(EditorWorkspacePolicy.calculate(700f, 340f, 2f, false).sideBySide)
        val tablet = EditorWorkspacePolicy.calculate(1000f, 800f, 2f, false)
        assertTrue(tablet.sideBySide)
        assertTrue(tablet.inspectorWidth >= 420f)
    }
}
