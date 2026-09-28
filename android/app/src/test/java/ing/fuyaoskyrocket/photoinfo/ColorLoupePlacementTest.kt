package ing.fuyaoskyrocket.photoinfo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import ing.fuyaoskyrocket.photoinfo.features.colors.ui.photo.placeColorLoupe
import org.junit.Assert.*
import org.junit.Test

class ColorLoupePlacementTest {
    @Test fun pressShowsLensAboveAndTopEdgeFlipsItBelow() {
        val panel = Size(390f, 300f)
        val normal = placeColorLoupe(Offset(195f, 210f), panel, 1f)
        assertEquals(100f, normal.diameter, 0f)
        assertEquals(Offset(195f, 132f), normal.center)
        assertFalse(normal.belowFinger)
        val top = placeColorLoupe(Offset(195f, 10f), panel, 1f)
        assertTrue(top.belowFinger)
        assertEquals(Offset(195f, 88f), top.center)
    }

    @Test fun cornersAndShortWindowsKeepTheEntireLensVisible() {
        for (panel in listOf(Size(390f, 300f), Size(768f, 500f), Size(80f, 60f))) {
            for (x in listOf(-20f, 0f, panel.width / 2, panel.width, panel.width + 20)) {
                for (y in listOf(-20f, 0f, panel.height / 2, panel.height, panel.height + 20)) {
                    val lens = placeColorLoupe(Offset(x, y), panel, 1f)
                    val radius = lens.diameter / 2
                    assertTrue(lens.center.x - radius >= 0)
                    assertTrue(lens.center.y - radius >= 0)
                    assertTrue(lens.center.x + radius <= panel.width)
                    assertTrue(lens.center.y + radius <= panel.height)
                }
            }
        }
    }

    @Test fun dimensionsFollowDisplayDensity() {
        val lens = placeColorLoupe(Offset(585f, 630f), Size(1170f, 900f), 3f)
        assertEquals(300f, lens.diameter, 0f)
        assertEquals(Offset(585f, 396f), lens.center)
    }
}
