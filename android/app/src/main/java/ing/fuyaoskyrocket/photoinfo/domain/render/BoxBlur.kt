package ing.fuyaoskyrocket.photoinfo.domain.render

/** Three separable box passes approximate a Gaussian. O(width*height), independent of radius. */
object BoxBlur {
    fun blur(input: IntArray, width: Int, height: Int, radius: Int): IntArray {
        require(width > 0 && height > 0 && width.toLong() * height == input.size.toLong()) { "blur plane ${width}x${height} vs ${input.size}" }
        if (radius <= 0) return input.copyOf()
        val r = radius.coerceAtMost(128)
        // Android getPixels returns unpremultiplied color; premultiply to avoid transparent color fringes.
        var a = IntArray(input.size) { index ->
            val c = input[index]; val alpha = c ushr 24
            (alpha shl 24) or ((((c ushr 16) and 255) * alpha / 255) shl 16) or
                ((((c ushr 8) and 255) * alpha / 255) shl 8) or ((c and 255) * alpha / 255)
        }
        var b = IntArray(input.size)
        repeat(3) {
            pass(a, b, width, height, r, true)
            val swap = a; a = b; b = swap
            pass(a, b, width, height, r, false)
            val swap2 = a; a = b; b = swap2
        }
        return IntArray(a.size) { index ->
            val c = a[index]; val alpha = c ushr 24
            if (alpha == 0) 0 else (alpha shl 24) or
                (((((c ushr 16) and 255) * 255 / alpha).coerceAtMost(255)) shl 16) or
                (((((c ushr 8) and 255) * 255 / alpha).coerceAtMost(255)) shl 8) or
                ((c and 255) * 255 / alpha).coerceAtMost(255)
        }
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val length = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val stride = if (horizontal) 1 else w
        val window = 2 * r + 1
        for (line in 0 until lines) {
            val start = if (horizontal) line * w else line
            var alpha = 0; var red = 0; var green = 0; var blue = 0
            for (i in -r..r) {
                val c = src[start + i.coerceIn(0, length - 1) * stride]
                alpha += c ushr 24; red += (c ushr 16) and 255
                green += (c ushr 8) and 255; blue += c and 255
            }
            for (pos in 0 until length) {
                dst[start + pos * stride] = ((alpha / window) shl 24) or
                    ((red / window) shl 16) or ((green / window) shl 8) or (blue / window)
                val old = src[start + (pos - r).coerceIn(0, length - 1) * stride]
                val new = src[start + (pos + r + 1).coerceIn(0, length - 1) * stride]
                alpha += (new ushr 24) - (old ushr 24)
                red += ((new ushr 16) and 255) - ((old ushr 16) and 255)
                green += ((new ushr 8) and 255) - ((old ushr 8) and 255)
                blue += (new and 255) - (old and 255)
            }
        }
    }
}
