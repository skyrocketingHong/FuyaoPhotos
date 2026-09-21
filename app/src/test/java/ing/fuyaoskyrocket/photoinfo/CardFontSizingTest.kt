package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.typography.CardFontSizing
import org.junit.Assert.*
import org.junit.Test

class CardFontSizingTest {
    @Test fun compactCapitalsMatchMonoAtEveryPhotoScale() {
        // Actual OS/2 sCapHeight values of the bundled font families, both in 2048 em units.
        val scale=CardFontSizing.matchCapHeight(1372f,1443f)
        assertEquals(1.0517493f,scale,.00001f)
        for (size in listOf(10.5f,37.55f,96f)) {
            assertEquals(size*1443/2048,size*scale*1372/2048,.0001f)
        }
    }
    @Test fun equalOrUnavailableMetricsDoNotDistortText() {
        assertEquals(1f,CardFontSizing.matchCapHeight(1443f,1443f),0f)
        assertEquals(1f,CardFontSizing.matchCapHeight(0f,1443f),0f)
        assertEquals(1f,CardFontSizing.matchCapHeight(Float.NaN,1443f),0f)
    }
}
