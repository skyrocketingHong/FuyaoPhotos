package ing.fuyaoskyrocket.photoinfo.features.colors.domain.color

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.os.Build
import android.util.Half
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun readSourcePixel(bitmap: Bitmap, x: Int, y: Int): Color {
    if (Build.VERSION.SDK_INT >= 29) return bitmap.getColor(x, y)

    // getPixel() converts wide-gamut pixels to 8-bit sRGB on API 26–28.
    // A one-pixel crop keeps the bitmap profile and avoids copying a full photograph per touch.
    val pixel = Bitmap.createBitmap(bitmap, x, y, 1, 1)
    try {
        val buffer = ByteBuffer.allocate(pixel.byteCount).order(ByteOrder.nativeOrder())
        pixel.copyPixelsToBuffer(buffer)
        buffer.rewind()
        val rgba = when (pixel.config) {
            Bitmap.Config.RGBA_F16 -> FloatArray(4) {
                val halfBits: Short = buffer.getShort()
                Half(halfBits).toFloat()
            }
            Bitmap.Config.ARGB_8888 -> FloatArray(4) { (buffer.get().toInt() and 0xff) / 255f }
            Bitmap.Config.RGB_565 -> {
                val bits = buffer.short.toInt() and 0xffff
                floatArrayOf((bits shr 11) / 31f, ((bits shr 5) and 63) / 63f, (bits and 31) / 31f, 1f)
            }
            else -> error("Unsupported source pixel format")
        }
        if (pixel.isPremultiplied && rgba[3] > 0f) {
            for (index in 0..2) rgba[index] /= rgba[3]
        }
        return Color.valueOf(rgba[0], rgba[1], rgba[2], rgba[3],
            pixel.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB))
    } finally {
        if (pixel !== bitmap) pixel.recycle()
    }
}
