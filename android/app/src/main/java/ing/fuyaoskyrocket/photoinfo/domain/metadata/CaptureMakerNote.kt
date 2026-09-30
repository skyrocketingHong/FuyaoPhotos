package ing.fuyaoskyrocket.photoinfo.domain.metadata

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads only the Apple capture fields used for labels; never modifies the original note. */
object CaptureMakerNote {
    data class Facts(val cameraType: Int? = null, val photographicStyle: String? = null)

    fun fromExif(payload: ByteArray?): ByteArray? = runCatching {
        require(payload != null && payload.size in 14..1_048_576)
        val marker = "Exif\u0000\u0000".toByteArray()
        val start = when {
            payload.size >= 10 && payload.copyOfRange(4, 10).contentEquals(marker) -> 10
            payload.copyOfRange(0, 6).contentEquals(marker) -> 6
            else -> error("Missing Exif marker")
        }
        val tiff = payload.copyOfRange(start, payload.size)
        require(tiff.size >= 14)
        val order = when (String(tiff, 0, 2, Charsets.US_ASCII)) {
            "MM" -> ByteOrder.BIG_ENDIAN
            "II" -> ByteOrder.LITTLE_ENDIAN
            else -> error("Invalid TIFF order")
        }
        val input = ByteBuffer.wrap(tiff).order(order)
        require(input.getShort(2).toInt() == 42)
        fun entry(offset: Int, tag: Int): Int? {
            require(offset >= 8 && offset <= tiff.size - 6)
            val count = input.getShort(offset).toInt() and 65535
            require(count <= 512 && offset + 6L + count * 12L <= tiff.size)
            return (0 until count).map { offset + 2 + it * 12 }.singleOrNull {
                input.getShort(it).toInt() and 65535 == tag
            }
        }
        val pointer = requireNotNull(entry(input.getInt(4), 0x8769))
        require(input.getShort(pointer + 2).toInt() == 4 && input.getInt(pointer + 4) == 1)
        val note = requireNotNull(entry(input.getInt(pointer + 8), 0x927c))
        require(input.getShort(note + 2).toInt() == 7)
        val size = input.getInt(note + 4)
        val offset = input.getInt(note + 8)
        require(size > 0 && offset >= 8 && offset.toLong() + size <= tiff.size)
        tiff.copyOfRange(offset, offset + size)
    }.getOrNull()

    fun read(note: ByteArray?): Facts {
        if (note == null || note.size !in 18..1_048_576 ||
            !note.copyOfRange(0, 14).contentEquals("Apple iOS\u0000\u0000\u0001MM".toByteArray())) return Facts()
        return runCatching {
            val input = ByteBuffer.wrap(note).order(ByteOrder.BIG_ENDIAN)
            val count = input.getShort(14).toInt() and 65535
            require(count <= 512 && 16L + count * 12L <= note.size)
            var camera: Int? = null
            var modern: Map<String, Any>? = null
            var legacy: Map<String, Any>? = null
            var hasModern = false
            repeat(count) { index ->
                val at = 16 + index * 12
                val tag = input.getShort(at).toInt() and 65535
                val type = input.getShort(at + 2).toInt() and 65535
                val length = input.getInt(at + 4).toLong() and 0xffffffffL
                if (tag == 46 && type in setOf(4, 9) && length == 1L) camera = input.getInt(at + 8)
                if (tag == 84) hasModern = true
                if (tag in setOf(64, 84) && type == 7 && length in 1..65_536) {
                    val offset = input.getInt(at + 8).toLong() and 0xffffffffL
                    if (offset >= 16L + count * 12L && offset <= note.size - length) {
                        val values = CapturePlist.dictionary(note.copyOfRange(offset.toInt(), (offset + length).toInt()))
                        if (tag == 84) modern = values else legacy = values
                    }
                }
            }
            val style = if (hasModern) modern?.let(PhotographicStyleReader::modernName) else {
                (legacy?.get("_3") as? Number)?.toDouble()
                    ?.takeIf { it.isFinite() && it in 1.0..5.0 && it == it.toInt().toDouble() }?.toInt()?.let {
                    mapOf(1 to "Standard", 2 to "Vibrant", 3 to "Rich Contrast", 4 to "Warm", 5 to "Cool")[it]
                }
            }
            Facts(camera, style)
        }.getOrDefault(Facts())
    }
}

/** Bounded flat dictionaries cover the native 91/139-byte style plists, including float32 values. */
internal object CapturePlist {
    fun dictionary(bytes: ByteArray): Map<String, Any>? = runCatching {
        require(bytes.size in 40..65_536 && bytes.copyOfRange(0, 8).contentEquals("bplist00".toByteArray()))
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val trailer = bytes.size - 32
        val offsetSize = bytes[trailer + 6].toInt() and 255
        val refSize = bytes[trailer + 7].toInt() and 255
        val count = input.getLong(trailer + 8)
        val root = input.getLong(trailer + 16)
        val table = input.getLong(trailer + 24)
        require(offsetSize in 1..4 && refSize in 1..2 && count in 1..256 && root in 0 until count)
        require(table >= 8 && table <= trailer - count * offsetSize)
        fun uint(at: Int, size: Int, end: Int): Int {
            require(size in 1..4 && at >= 8 && at <= end - size)
            var value = 0L
            repeat(size) { value = (value shl 8) or (bytes[at + it].toLong() and 255) }
            require(value <= Int.MAX_VALUE)
            return value.toInt()
        }
        fun objectAt(id: Int): Int {
            require(id in 0 until count.toInt())
            return uint(table.toInt() + id * offsetSize, offsetSize, trailer).also { require(it in 8 until table.toInt()) }
        }
        fun length(at: Int): Pair<Int, Int> {
            val small = bytes[at].toInt() and 15
            if (small < 15) return small to (at + 1)
            require(at + 1 < table && (bytes[at + 1].toInt() and 0xf0) == 0x10)
            val exponent = bytes[at + 1].toInt() and 15
            require(exponent <= 2)
            val size = 1 shl exponent
            return uint(at + 2, size, table.toInt()) to (at + 2 + size)
        }
        fun primitive(id: Int): Any {
            val at = objectAt(id)
            val marker = bytes[at].toInt() and 255
            return when (marker ushr 4) {
                0 -> when (marker) { 8 -> false; 9 -> true; else -> error("Unsupported plist primitive") }
                1 -> {
                    val exponent = marker and 15
                    require(exponent <= 3)
                    val size = 1 shl exponent
                    require(at + 1L + size <= table)
                    if (size == 8) input.getLong(at + 1) else uint(at + 1, size, table.toInt()).toLong()
                }
                2 -> {
                    val size = 1 shl (marker and 15)
                    require(size in setOf(4, 8) && at + 1L + size <= table)
                    (if (size == 4) input.getFloat(at + 1).toDouble() else input.getDouble(at + 1)).also { require(it.isFinite()) }
                }
                5, 6 -> {
                    val (size, start) = length(at)
                    val unit = if (marker ushr 4 == 6) 2 else 1
                    require(size <= 128 && start.toLong() + size * unit <= table)
                    String(bytes, start, size * unit, if (unit == 2) Charsets.UTF_16BE else Charsets.US_ASCII)
                }
                else -> error("Unsupported nested plist value")
            }
        }
        val at = objectAt(root.toInt())
        require((bytes[at].toInt() and 0xf0) == 0xd0)
        val (size, start) = length(at)
        require(size <= 32 && start.toLong() + size * refSize * 2 <= table)
        buildMap {
            repeat(size) { index ->
                val key = primitive(uint(start + index * refSize, refSize, table.toInt())) as? String ?: error("Invalid plist key")
                require(key !in this)
                put(key, primitive(uint(start + (index + size) * refSize, refSize, table.toInt())))
            }
        }
    }.getOrNull()
}
