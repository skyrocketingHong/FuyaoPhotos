package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.HeifImageContainer
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class HeifExifRoundTripTest {
    @Test fun exifItemSurvivesTheAssembledContainer() {
        // The self-assembled container keeps a standalone-readable Exif item: our reader finds
        // the item and its payload stays a valid APP1 block with the Exif prefix intact.
        val tiny = File.createTempFile("exif-tiny-", ".jpg")
        val assembled = File.createTempFile("exif-heif-", ".heic")
        try {
            tiny.writeBytes(minimalJpegWithExif())
            val exifBytes = JpegStub.exifOf(tiny) ?: error("fixture JPEG carries no EXIF")
            val item = HeifImageContainer.Item(1, "hvc1", byteArrayOf(0),
                byteArrayOf(0, 0, 0, 4, 0x26, 1, 0xaa.toByte(), 0xbb.toByte()),
                listOf(HeifImageContainer.Property(1, true)))
            val container = HeifImageContainer(false, 1, listOf(item),
                listOf(ispe(64, 48)), emptyList())
            container.withExif(exifBytes).write(assembled)

            val read = HeifImageContainer.read(assembled)
            val stored = read.items.firstOrNull { it.type == "Exif" } ?: error("Exif item missing after round trip")
            assertEquals("Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1).toList(),
                stored.payload.copyOfRange(0, 6).toList())
            assertArrayEquals(exifBytes, stored.payload)
        } finally { tiny.delete(); assembled.delete() }
    }

}

private fun ispe(width: Int, height: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val stream = java.io.DataOutputStream(out)
    stream.writeInt(0)
    stream.writeBytes("ispe")
    stream.writeInt(0)
    stream.writeInt(width)
    stream.writeInt(height)
    stream.flush()
    val bytes = out.toByteArray()
    bytes[3] = bytes.size.toByte()
    return bytes
}

private fun minimalJpegWithExif(): ByteArray {
    // Minimal TIFF: little-endend IFD0 with one Make entry, wrapped in a JPEG APP1.
    val tiff = java.io.ByteArrayOutputStream()
    fun u16(v: Int) { tiff.write(v and 255); tiff.write(v shr 8) }
    fun u32(v: Int) { u16(v and 0xffff); u16(v ushr 16) }
    tiff.write("II".toByteArray()); u16(42); u32(8)
    u16(1)
    u16(0x010f); u16(2); u32(6); u32(24) // Make, ASCII, 6 bytes, offset 24
    u32(0)
    tiff.write("Fuyao".toByteArray()); tiff.write(0)
    val app1 = java.io.ByteArrayOutputStream()
    val body = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff.toByteArray()
    app1.write(0xff); app1.write(0xe1)
    app1.write((body.size + 2) shr 8); app1.write((body.size + 2) and 255)
    app1.write(body)
    return byteArrayOf(0xff.toByte(), 0xd8.toByte()) + app1.toByteArray() +
        byteArrayOf(0xff.toByte(), 0xd9.toByte())
}

private object JpegStub {
    fun exifOf(file: File): ByteArray? {
        val bytes = file.readBytes()
        var at = 2
        while (at + 4 <= bytes.size) {
            if (bytes[at] != 0xff.toByte()) return null
            val marker = bytes[at + 1].toInt() and 255
            if (marker == 0xda || marker == 0xd9) return null
            val size = ((bytes[at + 2].toInt() and 255) shl 8) or (bytes[at + 3].toInt() and 255)
            if (marker == 0xe1 && bytes[at + 4] == 'E'.code.toByte()) {
                return bytes.copyOfRange(at + 4, at + 2 + size)
            }
            at += 2 + size
        }
        return null
    }
}
