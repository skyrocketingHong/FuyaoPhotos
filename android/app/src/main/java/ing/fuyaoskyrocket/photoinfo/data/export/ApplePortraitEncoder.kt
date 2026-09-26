package ing.fuyaoskyrocket.photoinfo.data.export

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import ing.fuyaoskyrocket.photoinfo.domain.media.ApplePortraitMetadata
import ing.fuyaoskyrocket.photoinfo.domain.media.HeifImageContainer
import ing.fuyaoskyrocket.photoinfo.domain.media.IsoBmff
import ing.fuyaoskyrocket.photoinfo.domain.media.XiaomiPortraitDepth
import ing.fuyaoskyrocket.photoinfo.platform.HevcEightBitStill
import java.io.File

/**
 * Attaches the Apple portrait auxiliary stack as single hvc1 items. Native captures and the
 * device-verified reference converter both carry each depth/matte plane as one item - the
 * platform HeifWriter instead tiles aux images into 512 grids on several firmwares.
 */
internal object ApplePortraitEncoder {
    fun attach(file: File, portrait: XiaomiPortraitDepth.Result, orientation: Int, aperture: Double? = null,
        calibration: ApplePortraitMetadata.Calibration = ApplePortraitMetadata.Calibration(0, 0, 0, 0, null, null, 0.0)) {
        var container = HeifImageContainer.read(file)
        val orientedDisparity = oriented(portrait.disparity, orientation)
        val disparityCalibration = calibration.copy(auxWidth = orientedDisparity.width, auxHeight = orientedDisparity.height)
        container = container.withAuxiliary(monoItem(orientedDisparity),
            ApplePortraitMetadata.DISPARITY, ApplePortraitMetadata.disparityXmp(aperture, disparityCalibration))
        portrait.matte?.let { matte ->
            container = container.withAuxiliary(monoItem(oriented(matte, orientation)),
                ApplePortraitMetadata.MATTE, ApplePortraitMetadata.matteXmp)
        }
        container.write(file)
        val verified = HeifImageContainer.read(file)
        check(verified.references.count { it.type == "auxl" } == container.references.count { it.type == "auxl" })
        container.items.zip(verified.items).forEach { (expected, actual) -> check(expected.payload.contentEquals(actual.payload)) }
    }

    /** One plain hvc1 item carrying the oriented mono plane. */
    private fun monoItem(pixels: Bitmap): HeifImageContainer {
        val encoded = try { encodeLuma(pixels) } finally { pixels.recycle() }
        return HeifImageContainer(false, 1,
            listOf(HeifImageContainer.Item(1, "hvc1", byteArrayOf(0), encoded.payload, listOf(
                HeifImageContainer.Property(1, true),
                HeifImageContainer.Property(2, false),
                HeifImageContainer.Property(3, true)))),
            listOf(IsoBmff.full("ispe", payload = IsoBmff.data {
                writeInt(encoded.width); writeInt(encoded.height)
            }), IsoBmff.full("pixi", payload = IsoBmff.data { write(1); write(8) }), encoded.hvcC),
            emptyList())
    }

    private class EncodedPlane(val width: Int, val height: Int, val hvcC: ByteArray, val payload: ByteArray)

    private fun encodeLuma(pixels: Bitmap): EncodedPlane {
        val width = pixels.width
        val height = pixels.height
        val luma = ByteArray(width * height)
        val row = IntArray(width)
        for (y in 0 until height) {
            pixels.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) luma[y * width + x] = (row[x] and 255).toByte()
        }
        val encoded = HevcEightBitStill.encodeMono(width, height, luma)
        return EncodedPlane(width, height, encoded.hvcC, encoded.payload)
    }

    private fun oriented(plane: XiaomiPortraitDepth.Plane, orientation: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(plane.width, plane.height, Bitmap.Config.ARGB_8888)
        try {
            val row = IntArray(plane.width)
            for (y in 0 until plane.height) {
                for (x in row.indices) {
                    val v = plane.pixels[y * plane.width + x].toInt() and 255
                    row[x] = Color.rgb(v, v, v)
                }
                bitmap.setPixels(row, 0, row.size, 0, y, row.size, 1)
            }
            if (orientation !in 2..8) return bitmap
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                    7 -> { setRotate(90f); postScale(1f, -1f) }; 8 -> setRotate(270f)
                }
            }
            val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (result !== bitmap) bitmap.recycle()
            return result
        } catch (failure: Throwable) { bitmap.recycle(); throw failure }
    }
}
