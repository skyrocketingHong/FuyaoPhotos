package ing.fuyaoskyrocket.photoinfo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ing.fuyaoskyrocket.photoinfo.domain.layout.CardBox
import ing.fuyaoskyrocket.photoinfo.domain.layout.CardLayout
import ing.fuyaoskyrocket.photoinfo.domain.layout.CardLine
import ing.fuyaoskyrocket.photoinfo.domain.layout.CardLayoutEngine
import ing.fuyaoskyrocket.photoinfo.domain.model.CardStyle
import ing.fuyaoskyrocket.photoinfo.domain.model.FieldId
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoInfo
import ing.fuyaoskyrocket.photoinfo.platform.CardTextRenderer
import ing.fuyaoskyrocket.photoinfo.platform.CardTypography
import ing.fuyaoskyrocket.photoinfo.platform.FontRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Requires Android shaping/rasterization. Compiling this class is not device validation. */
@RunWith(AndroidJUnit4::class)
class CardTypographyTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun reference()=FontRepository(context).defaultTypography

    @Test fun digitsAreMonospacedWhileBracketsAndSpacesUseTheLetterFace() {
        val typography=reference()
        val renderer=CardTextRenderer(typography,40f)
        assertEquals(renderer.measure("111"),renderer.measure("888"),.01f)
        assertTrue(renderer.measure("(")<renderer.measure("8"))
        assertTrue(renderer.measure(" ")<renderer.measure("8"))
        val text="24 MM (1X)"
        val styled=renderer.styledText(text) as Spanned
        val spans=styled.getSpans(0,text.length,MetricAffectingSpan::class.java)
        assertEquals(listOf("24","1"),spans.map { text.substring(styled.getSpanStart(it),styled.getSpanEnd(it)) })
        spans.forEach { span ->
            val measurement=TextPaint().apply { fontFeatureSettings="'cv05' 1" }
            val drawing=TextPaint(measurement)
            span.updateMeasureState(measurement);span.updateDrawState(drawing)
            assertSame(typography.numbers,measurement.typeface)
            assertSame(measurement.typeface,drawing.typeface)
            assertNull(drawing.fontFeatureSettings)
        }
    }

    @Test fun customFontsRemainUniformIncludingDigitsAndPunctuation() {
        val renderer=CardTextRenderer(CardTypography.uniform(Typeface.SERIF),40f)
        assertFalse(renderer.styledText("75 MM (3.2X)") is Spanned)
    }

    @Test fun colorTextAndHdrMaskHaveIdenticalGlyphCoverage() {
        val typography=reference()
        val layout=CardLayout(CardBox(0f,0f,720f,240f),listOf(
            CardLine("iPHONE 18 PRO",true,16f,16f),
            CardLine("FOCAL LENGTH: 75 MM (3.2X)",false,16f,72f),
            CardLine("PHOTO: 東京 1️⃣ 400",true,16f,128f)),28f,40f,0f,0f)
        val color=Bitmap.createBitmap(720,240,Bitmap.Config.ARGB_8888)
        val mask=Bitmap.createBitmap(720,240,Bitmap.Config.ARGB_8888)
        try {
            CardTextRenderer(typography,layout.fontSize).drawLines(Canvas(color),layout)
            CardTextRenderer(typography,layout.fontSize).drawLines(Canvas(mask),layout,mask=true)
            val a=IntArray(720*240);val b=IntArray(a.size)
            color.getPixels(a,0,720,0,0,720,240);mask.getPixels(b,0,720,0,0,720,240)
            assertTrue(a.any { Color.alpha(it)>0 })
            a.indices.forEach { assertEquals(Color.alpha(a[it]),Color.alpha(b[it])) }
        } finally { color.recycle();mask.recycle() }
    }

    @Test fun measuredWrappingUsesTheSameMixedFontsAtExportScale() {
        val typography=reference()
        val reference=CardTextRenderer(typography,CardLayoutEngine.FONT_SIZE)
        val layout=requireNotNull(CardLayoutEngine.layout(4080,3072,PhotoInfo(mapOf(
            FieldId.DEVICE to "XIAOMI 17 ULTRA BY LEICA",
            FieldId.LOCATION to "HONG KONG SPECIAL ADMINISTRATIVE REGION, CHINA",
            FieldId.FOCAL_LENGTH to "75 MM (3.2X)")),CardStyle(),reference::measure))
        val exported=CardTextRenderer(typography,layout.fontSize)
        val unit=layout.fontSize/CardLayoutEngine.FONT_SIZE
        layout.lines.forEach { assertTrue(exported.measure(it.text)<=177f*unit+2f) }
    }
}
