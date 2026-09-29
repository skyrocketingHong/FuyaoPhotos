package ing.fuyaoskyrocket.photoinfo.platform

import android.graphics.Bitmap
import ing.fuyaoskyrocket.photoinfo.domain.media.StyleSceneSample
import java.util.Arrays

/**
 * Measures the style scene data from the upright export bitmap. The export bakes the
 * capture orientation into the pixels, so the bitmap already is the stored frame and the
 * light-map grid needs only the 180-degree reversal. An RGBA_F16 source is read through
 * getPixels, which converts wide-gamut samples to sRGB code values.
 */
internal object StyleSceneSampler {
    fun sample(bitmap: Bitmap): StyleSceneSample {
        val histogram = luma(areaSampled(bitmap, 256, 192))
        Arrays.sort(histogram)
        val reversedGrid = luma(areaSampled(bitmap, 32, 32)).reversedArray()
        return StyleSceneSample(
            blackPoint = StyleSceneSample.percentile(histogram, 0.001),
            p02 = StyleSceneSample.percentile(histogram, 0.02),
            p10 = StyleSceneSample.percentile(histogram, 0.10),
            p25 = StyleSceneSample.percentile(histogram, 0.25),
            p50 = StyleSceneSample.percentile(histogram, 0.50),
            p75 = StyleSceneSample.percentile(histogram, 0.75),
            p98 = StyleSceneSample.percentile(histogram, 0.98),
            whitePoint = StyleSceneSample.percentile(histogram, 0.999),
            lightMapC = StyleSceneSample.fittedLightMap(reversedGrid,
                StyleSceneSample.C_SLOPE, StyleSceneSample.C_INTERCEPT),
            lightMapD = StyleSceneSample.fittedLightMap(reversedGrid,
                StyleSceneSample.D_SLOPE, StyleSceneSample.D_INTERCEPT))
    }

    /** Halving steps approximate area averaging before the final filtered scale. */
    private fun areaSampled(source: Bitmap, width: Int, height: Int): Bitmap {
        var current = source
        while (current.width / 2 >= width && current.height / 2 >= height) {
            current = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
        }
        return Bitmap.createScaledBitmap(current, width, height, true)
    }

    private fun luma(bitmap: Bitmap): DoubleArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val linear = StyleSceneSample.srgbLinearTable()
        val out = DoubleArray(pixels.size)
        for (index in pixels.indices) {
            val color = pixels[index]
            out[index] = StyleSceneSample.linearLuma(
                (color shr 16) and 255, (color shr 8) and 255, color and 255, linear)
        }
        return out
    }
}
