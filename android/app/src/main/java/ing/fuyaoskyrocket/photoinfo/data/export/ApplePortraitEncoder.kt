package ing.fuyaoskyrocket.photoinfo.data.export

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import ing.fuyaoskyrocket.photoinfo.domain.media.ApplePortraitMetadata
import ing.fuyaoskyrocket.photoinfo.domain.media.HeifImageContainer
import ing.fuyaoskyrocket.photoinfo.domain.media.XiaomiPortraitDepth
import java.io.File

internal object ApplePortraitEncoder {
    fun attach(file: File, portrait: XiaomiPortraitDepth.Result, orientation: Int, aperture: Double? = null) {
        var container = HeifImageContainer.read(file)
        val temp = File.createTempFile("portrait-plane-", ".heic", file.parentFile)
        try {
            fun append(plane: XiaomiPortraitDepth.Plane, type: String, xmp: String) {
                val pixels = oriented(plane, orientation)
                try { HeicEncoder.encode(pixels, temp, 100, null, emptyMap()) }
                finally { pixels.recycle() }
                container = container.withAuxiliary(HeifImageContainer.read(temp), type, xmp)
            }
            append(portrait.disparity, ApplePortraitMetadata.DISPARITY, ApplePortraitMetadata.disparityXmp(aperture))
            portrait.matte?.let { append(it, ApplePortraitMetadata.MATTE, ApplePortraitMetadata.matteXmp) }
            container.write(file)
            val verified = HeifImageContainer.read(file)
            check(verified.references.count { it.type == "auxl" } == container.references.count { it.type == "auxl" })
            container.items.zip(verified.items).forEach { (expected, actual) -> check(expected.payload.contentEquals(actual.payload)) }
        } finally { temp.delete() }
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
