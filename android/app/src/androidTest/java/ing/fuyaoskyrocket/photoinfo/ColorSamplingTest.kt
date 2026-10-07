package ing.fuyaoskyrocket.photoinfo

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.util.Half
import androidx.test.ext.junit.runners.AndroidJUnit4
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.color.samplePixel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class ColorSamplingTest {
    @Test fun linearSourceComponentsDifferFromConvertedSrgb() {
        withPixel(ColorSpace.Named.LINEAR_EXTENDED_SRGB, .25f, .5f, .75f) { bitmap ->
            val sample = samplePixel(bitmap, 0, 0)
            assertEquals(.25, sample.sourceRgb.redComponent, .001)
            assertEquals(.5, sample.sourceRgb.greenComponent, .001)
            assertEquals(.75, sample.sourceRgb.blueComponent, .001)
            assertEquals(.537099, sample.sRgb.redComponent, .002)
            assertEquals("srgb-linear", sample.sourceCssSpace)
        }
    }

    @Test fun wideGamutSourceIsNotReadThroughGetPixelSrgb() {
        withPixel(ColorSpace.Named.DISPLAY_P3, 1f, 0f, 0f) { bitmap ->
            val sample = samplePixel(bitmap, 0, 0)
            assertEquals(1.0, sample.sourceRgb.redComponent, .001)
            assertEquals(0.0, sample.sourceRgb.greenComponent, .001)
            assertTrue(sample.sRgb.redComponent > 1)
            assertTrue(sample.sRgb.greenComponent < 0)
            assertEquals(1.0, sample.displayP3.redComponent, .003)
            assertEquals(0.0, sample.displayP3.greenComponent, .003)
        }
    }

    @Test fun sourceComponentsAreUnpremultiplied() {
        withPixel(ColorSpace.Named.DISPLAY_P3, .125f, .25f, .375f, alpha = .5f) { bitmap ->
            val sample = samplePixel(bitmap, 0, 0)
            assertEquals(.25, sample.sourceRgb.redComponent, .003)
            assertEquals(.5, sample.sourceRgb.greenComponent, .003)
            assertEquals(.75, sample.sourceRgb.blueComponent, .003)
        }
    }

    private fun withPixel(space: ColorSpace.Named, red: Float, green: Float, blue: Float,
        alpha: Float = 1f, test: (Bitmap) -> Unit) {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGBA_F16, true, ColorSpace.get(space))
        try {
            val data = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
            listOf(red, green, blue, alpha).forEach { data.putShort(Half.toHalf(it)) }
            data.rewind()
            bitmap.copyPixelsFromBuffer(data)
            test(bitmap)
        } finally { bitmap.recycle() }
    }
}
