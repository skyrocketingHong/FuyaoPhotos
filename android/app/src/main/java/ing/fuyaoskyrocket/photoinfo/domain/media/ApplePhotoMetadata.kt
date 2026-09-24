package ing.fuyaoskyrocket.photoinfo.domain.media

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

internal object ApplePhotoMetadata {
    fun withIdentifier(exif: ByteArray?, identifier: String): ByteArray {
        require(UUID.fromString(identifier).toString().equals(identifier, true))
        val note = IsoBmff.data {
            write("Apple iOS\u0000\u0000\u0001MM".toByteArray()); writeShort(1)
            writeShort(0x0011); writeShort(2); writeInt(37); writeInt(32); writeInt(0)
            writeBytes(identifier.uppercase(java.util.Locale.ROOT)); writeByte(0)
        }
        return withMakerNote(exif,note)
    }

    fun withPortrait(exif: ByteArray?): ByteArray = withMakerNote(exif,IsoBmff.data {
        write("Apple iOS\u0000\u0000\u0001MM".toByteArray()); writeShort(1)
        writeShort(0x0014); writeShort(9); writeInt(1); writeInt(2); writeInt(0)
    })

    private fun withMakerNote(exif: ByteArray?, note: ByteArray): ByteArray {
        val prefix = "Exif\u0000\u0000".toByteArray()
        val source = if (exif == null) byteArrayOf(0x4d, 0x4d, 0, 42, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0) else {
            require(exif.size <= 60_000 && exif.take(6).toByteArray().contentEquals(prefix))
            exif.copyOfRange(6, exif.size)
        }
        require(source.size >= 14)
        val order = when (String(source, 0, 2, Charsets.US_ASCII)) {
            "MM" -> ByteOrder.BIG_ENDIAN; "II" -> ByteOrder.LITTLE_ENDIAN; else -> error("Invalid TIFF")
        }
        val input = ByteBuffer.wrap(source).order(order)
        require(input.getShort(2).toInt() == 42)
        fun entries(at: Int): List<ByteArray> {
            require(at >= 8 && at <= source.size - 6)
            val count = input.getShort(at).toInt() and 65535
            require(count <= 512 && at + 6L + count * 12L <= source.size)
            return List(count) { source.copyOfRange(at + 2 + it * 12, at + 14 + it * 12) }
        }
        fun tag(entry: ByteArray) = ByteBuffer.wrap(entry).order(order).short.toInt() and 65535
        fun entry(tag: Int, type: Int, count: Int, value: Int) = ByteBuffer.allocate(12).order(order).apply {
            putShort(tag.toShort()); putShort(type.toShort()); putInt(count); putInt(value)
        }.array()
        val root = entries(input.getInt(4))
        val previous = root.singleOrNull { tag(it) == 0x8769 }?.let { entries(ByteBuffer.wrap(it).order(order).getInt(8)) }.orEmpty()
        val noteAt = (source.size + 1) and -2
        val exifAt = (noteAt + note.size + 1) and -2
        val fields = (previous.filter { tag(it) != 0x927c } + listOf(entry(0x927c, 7, note.size, noteAt))).sortedBy(::tag)
        val rootAt = exifAt + 6 + fields.size * 12
        val rootFields = (root.filter { tag(it) != 0x8769 } + listOf(entry(0x8769, 4, 1, exifAt))).sortedBy(::tag)
        val output = ByteBuffer.allocate(rootAt + 6 + rootFields.size * 12).order(order)
        output.put(source); output.putInt(4, rootAt)
        output.position(noteAt); output.put(note)
        output.position(exifAt); output.putShort(fields.size.toShort()); fields.forEach(output::put); output.putInt(0)
        output.position(rootAt); output.putShort(rootFields.size.toShort()); rootFields.forEach(output::put); output.putInt(0)
        require(output.capacity() + 6 <= 65533)
        return prefix + output.array()
    }
}
