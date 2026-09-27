package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.layout.PageGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollEdgeProgressTest {
    @Test fun staysClearUntilContentReachesTopProtection() {
        assertEquals(0f, PageGeometry.scrollEdgeProgress(0f), 0f)
        assertEquals(0f, PageGeometry.scrollEdgeProgress(PageGeometry.MARGIN), 0f)
    }

    @Test fun fadesInAfterOverlapAndClearsOnReturn() {
        assertEquals(.5f, PageGeometry.scrollEdgeProgress(PageGeometry.MARGIN + 12f), .001f)
        assertEquals(1f, PageGeometry.scrollEdgeProgress(1_000f), 0f)
        assertEquals(0f, PageGeometry.scrollEdgeProgress(-10f), 0f)
    }
}
