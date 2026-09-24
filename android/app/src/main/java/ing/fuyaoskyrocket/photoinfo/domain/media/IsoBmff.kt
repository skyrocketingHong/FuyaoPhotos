package ing.fuyaoskyrocket.photoinfo.domain.media

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object IsoBmff {
    const val MAX_BYTES = 128 * 1024 * 1024
    data class Box(val type: String, val start: Int, val payload: Int, val end: Int) {
        fun raw(bytes: ByteArray) = bytes.copyOfRange(start, end)
    }

    fun boxes(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): List<Box> {
        require(start >= 0 && end in start..bytes.size && bytes.size <= MAX_BYTES)
        val result = ArrayList<Box>()
        var at = start
        while (at < end) {
            require(end - at >= 8 && result.size < 4096) { "Invalid box table" }
            val size32 = uint(bytes, at)
            val header = if (size32 == 1L) 16 else 8
            require(end - at >= header)
            val size = when (size32) {
                0L -> (end - at).toLong()
                1L -> ByteBuffer.wrap(bytes, at + 8, 8).long
                else -> size32
            }
            require(size in header.toLong()..(end - at).toLong()) { "Invalid box length" }
            result += Box(String(bytes, at + 4, 4, Charsets.ISO_8859_1), at, at + header, at + size.toInt())
            at += size.toInt()
        }
        return result
    }

    fun uint(bytes: ByteArray, at: Int): Long {
        require(at >= 0 && at <= bytes.size - 4)
        return ByteBuffer.wrap(bytes, at, 4).int.toLong() and 0xffffffffL
    }

    fun data(block: DataOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().let { out ->
        DataOutputStream(out).use { it.block() }; out.toByteArray()
    }
    fun box(type: String, vararg payload: ByteArray): ByteArray = data {
        require(type.length == 4)
        val length = 8L + payload.sumOf { it.size.toLong() }
        require(length <= MAX_BYTES)
        writeInt(length.toInt()); write(type.toByteArray(Charsets.ISO_8859_1))
        payload.forEach(::write)
    }
    fun full(type: String, version: Int = 0, flags: Int = 0, payload: ByteArray) =
        box(type, data { writeInt((version shl 24) or flags); write(payload) })

    class Reader(bytes: ByteArray, start: Int, end: Int) {
        private val buffer = ByteBuffer.wrap(bytes, start, end - start).slice().order(ByteOrder.BIG_ENDIAN)
        fun u8() = buffer.get().toInt() and 255
        fun u16() = buffer.short.toInt() and 65535
        fun u32() = buffer.int.toLong() and 0xffffffffL
        fun id32() = u32().also { require(it <= Int.MAX_VALUE) }.toInt()
        fun variable(size: Int): Long {
            require(size in setOf(0, 4, 8))
            return when (size) { 0 -> 0; 4 -> u32(); else -> buffer.long.also { require(it >= 0) } }
        }
        fun skip(size: Int) { require(size in 0..buffer.remaining()); buffer.position(buffer.position() + size) }
        fun fourCC() = ByteArray(4).also(buffer::get).toString(Charsets.ISO_8859_1)
        fun remaining() = buffer.remaining()
    }
}
