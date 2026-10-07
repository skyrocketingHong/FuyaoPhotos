package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.features.colors.domain.color.colorRepresentationsFrom
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.color.colorValueFromComponents
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class ColorNumericValuesTest {
    @Test fun integerPresentationNeverChangesSourceComponents() {
        val value = colorValueFromComponents(-0.12, 0.5001, 1.25)
        assertEquals("#0080FF", value.hex)
        assertEquals(-0.12, value.redComponent, 0.0)
        assertEquals(0.5001, value.greenComponent, 0.0)
        assertEquals(1.25, value.blueComponent, 0.0)
        assertTrue(value.wasClamped)
        assertEquals("color(srgb -0.12 0.5001 1.25)", value.cssColorFunction("srgb"))
    }

    @Test fun rec2020UsesCssBt1886AndKeepsHdrHeadroom() {
        for (linear in listOf(0.01, 0.18, 1.0, 4.0)) {
            val values = colorRepresentationsFrom(colorValueFromComponents(0.5, 0.5, 0.5),
                floatArrayOf((0.9642956764 * linear).toFloat(), linear.toFloat(), (0.8251046025 * linear).toFloat()))
            val expected = linear.pow(1.0 / 2.4)
            assertEquals(expected, values.cssRec2020.red, 0.0001)
            assertEquals(expected, values.cssRec2020.green, 0.0001)
            assertEquals(expected, values.cssRec2020.blue, 0.0001)
            val rgb = values.cssRec2020.let { colorValueFromComponents(it.red, it.green, it.blue) }
            assertEquals(values.cssRec2020.cssText("rec2020"), rgb.cssColorFunction("rec2020"))
        }
    }

    @Test fun neutralWhiteAndBlackUseD50Reference() {
        val white = colorRepresentationsFrom(colorValueFromComponents(1.0, 1.0, 1.0),
            floatArrayOf(0.9642957f, 1f, 0.8251046f))
        assertEquals(100.0, white.cieLab.lightness, 0.001)
        assertEquals(0.0, white.cieLab.a, 0.001)
        assertEquals(0.0, white.cieLab.b, 0.001)
        val black = colorRepresentationsFrom(colorValueFromComponents(0.0, 0.0, 0.0), floatArrayOf(0f, 0f, 0f))
        assertEquals(0.0, black.xyzD65.y, 0.0)
        assertEquals(1.0, black.cmyk.black, 0.0)
    }

    @Test fun hslDoesNotDiscardExtendedSrgbComponents() {
        val values = colorRepresentationsFrom(colorValueFromComponents(1.2, 0.2, -0.1), floatArrayOf(0f, 0f, 0f))
        assertEquals(0.55, values.hsl.lightness, 0.000001)
        assertTrue(values.hsl.saturation > 1.0)
        val grey = colorRepresentationsFrom(colorValueFromComponents(0.5, 0.5, 0.5), floatArrayOf(0f, 0f, 0f))
        assertTrue(grey.hsl.cssText.startsWith("hsl(none "))
    }
}
