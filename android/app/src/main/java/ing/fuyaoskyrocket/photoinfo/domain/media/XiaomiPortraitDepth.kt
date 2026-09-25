package ing.fuyaoskyrocket.photoinfo.domain.media

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.Inflater

internal object XiaomiPortraitDepth {
    data class Plane(val width: Int, val height: Int, val pixels: ByteArray)
    data class Result(val disparity: Plane, val matte: Plane?, val sourceWidth: Int, val sourceHeight: Int, val orientation: Int)

    private const val FORMAT_KEY = "OTEzMTAxMDRNQTFGUkY1SjhHWE1EU1NGWmN6eWQxNgA="
    private const val MAX_PLANE = 16 * 1024 * 1024

    fun decode(bytes: ByteArray, depthDegrees: Int = -1, originDegrees: Int = 0): Result {
        val layout = XiaomiPortraitTail.layout(bytes)
        val trailer = bytes.copyOfRange(layout.secondEnd, bytes.size)
        val end = marker(trailer, "MCBOKEHEOT")
        require(end >= 32)
        val decoded = trailer.copyOf(end)
        val plaintext = "MCSAMPLINGAUX".toByteArray()
        if (!plaintext.indices.all { decoded[16 + it] == plaintext[it] }) descramble(decoded)
        val interfaceAt = marker(decoded, "MCBOKEHINTF")
        require(int(decoded, interfaceAt + 16) == 320 && interfaceAt + 340 <= decoded.size)
        val width = int(decoded, interfaceAt + 20)
        val height = int(decoded, interfaceAt + 24)
        val sourceWidth = int(decoded, interfaceAt + 28)
        val sourceHeight = int(decoded, interfaceAt + 32)
        // The depth plane lives in the depthOrientation-rotated space of the vendor capture;
        // aligning it with the stored photo takes the inverse rotation, composed with the
        // photo's own EXIF turn exactly like the vendor Bokeh editor does.
        val rotation = if(depthDegrees in 0..359) ((originDegrees - depthDegrees) + 720) % 360
            else int(decoded, interfaceAt + 52)
        val orientation = when(rotation) { 0->1; 90->6; 180->3; 270->8; else->error("Unknown portrait orientation") }
        require(width in 1..8192 && height in 1..8192 && width.toLong() * height <= MAX_PLANE)
        require(sourceWidth in 1..32768 && sourceHeight in 1..32768 &&
            width.toLong() * sourceHeight == height.toLong() * sourceWidth) { "Portrait geometry is unsupported" }
        val at = marker(decoded, "MCBOKEHDEPTH")
        val ranks = inflate(decoded, at + 20, int(decoded, at + 16), width * height)
        require(ranks.any { it != ranks[0] }) { "Portrait depth is empty" }
        // The vendor rank increases toward the background; Apple disparity increases toward the camera.
        val disparity = ByteArray(ranks.size) { (255 - (ranks[it].toInt() and 255)).toByte() }
        val matteAt = markerOrNull(decoded, "MCBOKEHAIMASK") ?: markerOrNull(decoded, "MCBOKEHMASK")
        val matte = matteAt?.let {
            val w = int(decoded, it + 16); val h = int(decoded, it + 20)
            require(w in 1..8192 && h in 1..8192 && w.toLong() * h <= MAX_PLANE)
            require(w.toLong() * sourceHeight == h.toLong() * sourceWidth)
            Plane(w, h, inflate(decoded, it + 28, int(decoded, it + 24), w * h))
        }
        return Result(Plane(width, height, disparity), matte, sourceWidth, sourceHeight, orientation)
    }

    private fun int(bytes: ByteArray, offset: Int): Int {
        require(offset >= 0 && offset <= bytes.size - 4)
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.also { require(it >= 0) }
    }

    private fun inflate(bytes: ByteArray, offset: Int, length: Int, expected: Int): ByteArray {
        require(length in 1..bytes.size && offset >= 0 && offset <= bytes.size - length && expected in 1..MAX_PLANE)
        val count = bytes[offset].toInt() and 255
        require(count in 1..32 && length > 1 + count * 4)
        val result = ByteArray(expected)
        var sourceAt = offset + 1 + count * 4
        var outputAt = 0
        repeat(count) { index ->
            val size = int(bytes, offset + 1 + index * 4)
            require(size > 0 && size <= offset + length - sourceAt)
            val inflater = Inflater()
            try {
                inflater.setInput(bytes, sourceAt, size)
                while (!inflater.finished() && outputAt < expected) {
                    val n = inflater.inflate(result, outputAt, expected - outputAt)
                    require(n > 0) { "Portrait depth stream is truncated" }
                    outputAt += n
                }
                require(inflater.finished() && inflater.remaining == 0) { "Portrait depth length differs" }
            } finally { inflater.end() }
            sourceAt += size
        }
        require(sourceAt == offset + length && outputAt == expected)
        return result
    }

    private fun marker(bytes: ByteArray, name: String) = requireNotNull(markerOrNull(bytes, name)) { "Missing portrait block" }
    private fun markerOrNull(bytes: ByteArray, name: String): Int? {
        val signature = name.toByteArray(Charsets.US_ASCII).copyOf(16)
        var result: Int? = null
        for (at in 0..bytes.size - 16) if (signature.indices.all { bytes[at + it] == signature[it] }) {
            require(result == null) { "Ambiguous portrait block" }; result = at
        }
        return result
    }

    // Format compatibility only: the vendor repeats the first 256 RC4 bytes, leaving its signature clear.
    internal fun descramble(bytes: ByteArray) {
        val key = Base64.getDecoder().decode(FORMAT_KEY)
        val state = IntArray(256) { it }
        var j = 0
        for (i in state.indices) {
            j = (j + state[i] + (key[i % key.size].toInt() and 255)) and 255
            val swap = state[i]; state[i] = state[j]; state[j] = swap
        }
        val stream = IntArray(256)
        var i = 0; j = 0
        for (n in stream.indices) {
            i = (i + 1) and 255; j = (j + state[i]) and 255
            val swap = state[i]; state[i] = state[j]; state[j] = swap
            stream[n] = state[(state[i] + state[j]) and 255]
        }
        for (n in 16 until bytes.size) bytes[n] = (bytes[n].toInt() xor stream[n and 255]).toByte()
    }
}
