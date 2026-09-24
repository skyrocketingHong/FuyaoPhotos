package ing.fuyaoskyrocket.photoinfo.data.export

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.os.Build
import androidx.heifwriter.HeifWriter
import androidx.heifwriter.AvifWriter
import ing.fuyaoskyrocket.photoinfo.domain.media.HeifImageContainer
import ing.fuyaoskyrocket.photoinfo.platform.AndroidGainMapMetadata
import androidx.exifinterface.media.ExifInterface
import java.io.File

internal object HeicEncoder {
    fun encode(bitmap: Bitmap, destination: File, quality: Int, exif: ByteArray?,
        requiredTags: Map<String, String>, avif: Boolean = false) {
        require(Build.VERSION.SDK_INT >= if (avif) 34 else 28)
        val gainmap = if (Build.VERSION.SDK_INT >= 34) bitmap.gainmap else null
        val baseFile = File.createTempFile("base-", ".heif", destination.parentFile)
        val gainFile = File.createTempFile("gain-", ".heif", destination.parentFile)
        try {
            if (Build.VERSION.SDK_INT >= 34) bitmap.setGainmap(null)
            encodePlane(bitmap, baseFile, quality, exif, avif)
            val encodedBase = HeifImageContainer.read(baseFile)
            val space = bitmap.colorSpace
            val encoding = when {
                space?.id==ColorSpace.Named.SRGB.ordinal -> 1 to 13
                space?.id==ColorSpace.Named.BT709.ordinal -> 1 to 1
                space?.id==ColorSpace.Named.BT2020.ordinal -> 9 to 14
                space?.id==ColorSpace.Named.DISPLAY_P3.ordinal -> 12 to 13
                Build.VERSION.SDK_INT>=34 && space?.id==ColorSpace.Named.BT2020_PQ.ordinal -> 9 to 16
                Build.VERSION.SDK_INT>=34 && space?.id==ColorSpace.Named.BT2020_HLG.ordinal -> 9 to 18
                else -> null
            }
            val base = if(encoding!=null)encodedBase.withColorSpace(encoding.first,encoding.second) else encodedBase
            if(bitmap.config==Bitmap.Config.RGBA_F16)require(base.bitDepth>=10) { "Image precision was not preserved" }
            require(base.avif == avif)
            if (Build.VERSION.SDK_INT >= 34 && gainmap != null) {
                val metadata = AndroidGainMapMetadata.read(gainmap)
                val original = gainmap.gainmapContents
                // ALPHA_8 contains gain values in alpha, not black RGB with transparency.
                val plane = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
                try {
                    val row = IntArray(original.width)
                    val alpha = if (original.config == Bitmap.Config.ALPHA_8) {
                        java.nio.ByteBuffer.allocate(original.byteCount).also(original::copyPixelsToBuffer).array()
                    } else null
                    for (y in 0 until original.height) {
                        if (alpha == null) original.getPixels(row, 0, row.size, 0, y, row.size, 1)
                        for (x in row.indices) {
                            row[x] = if (alpha == null) row[x] or Color.BLACK else {
                                val v = alpha[y * original.rowBytes + x].toInt() and 255
                                Color.rgb(v, v, v)
                            }
                        }
                        plane.setPixels(row, 0, row.size, 0, y, row.size, 1)
                    }
                    encodePlane(plane, gainFile, 100, null, avif)
                    base.withGainMap(HeifImageContainer.read(gainFile), metadata).write(destination)
                } finally { plane.recycle() }
                val result = HeifImageContainer.read(destination)
                check(result.items.single { it.type == "tmap" }.payload.contentEquals(metadata.strictToneMap()))
            } else base.write(destination)
            if (requiredTags.isNotEmpty()) {
                val written = ExifInterface(destination)
                check(requiredTags.keys.all { !written.getAttribute(it).isNullOrBlank() }) { "Capture metadata was not preserved" }
            }
        } finally {
            if (Build.VERSION.SDK_INT >= 34) bitmap.setGainmap(gainmap)
            baseFile.delete(); gainFile.delete()
        }
    }

    @SuppressLint("RestrictedApi")
    private fun encodePlane(bitmap: Bitmap, output: File, quality: Int, exif: ByteArray?, avif: Boolean) {
        if (avif) {
            val writer = AvifWriter.Builder(output.absolutePath, bitmap.width, bitmap.height, AvifWriter.INPUT_MODE_BITMAP)
                .setMaxImages(1).setQuality(quality.coerceIn(0, 100))
                .setHighBitDepthEnabled(bitmap.config==Bitmap.Config.RGBA_F16).build()
            try {
                writer.start()
                if (exif != null) writer.addExifData(0, exif, 0, exif.size)
                writer.addBitmap(bitmap); writer.stop(60_000)
            } finally { writer.close() }
        } else {
            val writer = HeifWriter.Builder(output.absolutePath, bitmap.width, bitmap.height, HeifWriter.INPUT_MODE_BITMAP)
                .setMaxImages(1).setQuality(quality.coerceIn(0, 100)).build()
            try {
                writer.start()
                if (exif != null) writer.addExifData(0, exif, 0, exif.size)
                writer.addBitmap(bitmap); writer.stop(60_000)
            } finally { writer.close() }
        }
    }
}
