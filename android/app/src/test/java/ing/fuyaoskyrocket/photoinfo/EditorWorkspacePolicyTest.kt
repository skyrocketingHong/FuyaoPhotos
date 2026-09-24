package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.layout.EditorWorkspacePolicy
import org.junit.Assert.*
import org.junit.Test

class EditorWorkspacePolicyTest {
    @Test fun portraitStacksPhotoAboveEditor() {
        val portrait = EditorWorkspacePolicy.calculate(600f, 760f, 1f, false)
        assertFalse(portrait.sideBySide)
        assertTrue(760f - portrait.previewHeight >= 180f)
        assertFalse(EditorWorkspacePolicy.calculate(800f, 1000f, 1f, false).sideBySide)
    }

    @Test fun landscapeSplitsOnlyWhenPhotoAndInspectorBothFit() {
        val landscape = EditorWorkspacePolicy.calculate(760f, 340f, 1f, false)
        assertTrue(landscape.sideBySide)
        assertEquals(360f, landscape.inspectorWidth, 0f)
        assertTrue(760f - landscape.inspectorWidth - EditorWorkspacePolicy.PANE_GAP >= EditorWorkspacePolicy.MIN_PHOTO_WIDTH)
        assertFalse(EditorWorkspacePolicy.calculate(651f, 340f, 1f, false).sideBySide)
    }

    @Test fun portraitReservesTheActionStripOutsideThePhoto() {
        val layout=EditorWorkspacePolicy.calculate(400f,900f,1f,false)
        assertEquals(352f,layout.previewHeight,0f)
        assertEquals(548f,900f-layout.previewHeight,0f)
    }

    @Test fun keyboardKeepsThePhotoVisibleInShortWindows() {
        val compact = EditorWorkspacePolicy.calculate(393f, 220f, 1f, true)
        assertEquals(120f, compact.previewHeight, 0f)
        assertTrue(220f - compact.previewHeight >= 96f)
        assertTrue(EditorWorkspacePolicy.calculate(393f, 400f, 1f, true).previewHeight <= 120f)
        assertTrue(EditorWorkspacePolicy.calculate(393f, 100f, 1f, true).previewHeight > 60f)
    }

    @Test fun largeTextNeedsAWiderInspector() {
        assertFalse(EditorWorkspacePolicy.calculate(700f, 340f, 2f, false).sideBySide)
        val tablet = EditorWorkspacePolicy.calculate(1000f, 800f, 2f, false)
        assertTrue(tablet.sideBySide)
        assertTrue(tablet.inspectorWidth >= 420f)
    }

    @Test fun separatingFoldRequiresEnoughRoomInEachPane() {
        assertTrue(EditorWorkspacePolicy.canSplitAcrossFold(280f, 360f, 1f))
        assertFalse(EditorWorkspacePolicy.canSplitAcrossFold(279f, 360f, 1f))
        assertFalse(EditorWorkspacePolicy.canSplitAcrossFold(280f, 359f, 1f))
        assertFalse(EditorWorkspacePolicy.canSplitAcrossFold(280f, 400f, 2f))
        assertTrue(EditorWorkspacePolicy.canSplitAcrossFold(280f, 420f, 2f))
    }
}
