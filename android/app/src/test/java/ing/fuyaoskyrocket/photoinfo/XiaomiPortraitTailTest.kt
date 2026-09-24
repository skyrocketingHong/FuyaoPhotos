package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.JpegContainer
import ing.fuyaoskyrocket.photoinfo.domain.media.XiaomiPortraitTail
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class XiaomiPortraitTailTest {
    private fun fixture(): ByteArray {
        val tiff = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN)
        tiff.put(byteArrayOf('I'.code.toByte(), 'I'.code.toByte()))
        tiff.putShort(42).putInt(8)
        tiff.position(8)
        tiff.putShort(2)
        tiff.putShort(0x0132).putShort(2).putInt(20).putInt(38)
        tiff.putShort(0x8825.toShort()).putShort(4).putInt(1).putInt(58)
        tiff.putInt(0)
        tiff.position(38)
        tiff.put("2026:08:02 10:46:50\u0000".toByteArray(Charsets.US_ASCII))
        tiff.position(58)
        tiff.putShort(1)
        tiff.putShort(2).putShort(5).putInt(3).putInt(76)
        tiff.putInt(0)
        tiff.position(76)
        repeat(3) { tiff.putInt(30).putInt(1) }
        val exif = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII) + tiff.array()
        val marker = byteArrayOf(0xff.toByte(), 0xe1.toByte(),
            ((exif.size + 2) ushr 8).toByte(), (exif.size + 2).toByte())
        val xmp = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII) +
            """<x:xmpmeta xmlns:x="adobe:ns:meta/" xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:exif="http://ns.adobe.com/exif/1.0/" xmlns:xmp="http://ns.adobe.com/xap/1.0/"><rdf:RDF><rdf:Description exif:GPSLatitude="30" xmp:CreateDate="2026-08-02T10:46:50"/></rdf:RDF></x:xmpmeta>"""
                .toByteArray(Charsets.UTF_8) + ByteArray(256) { ' '.code.toByte() }
        val xmpMarker = byteArrayOf(0xff.toByte(), 0xe1.toByte(),
            ((xmp.size + 2) ushr 8).toByte(), (xmp.size + 2).toByte())
        val tinyJpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())
        return tinyJpeg.copyOfRange(0, 2) + marker + exif + xmpMarker + xmp + tinyJpeg.copyOfRange(2, 4) +
            tinyJpeg + "MCBOKEHSOT".toByteArray(Charsets.US_ASCII) + ByteArray(256) { it.toByte() }
    }

    @Test fun locationTimeAndExifSelectionsDoNotMovePortraitData() {
        val original = fixture()
        val file = File.createTempFile("xiaomi-tail-", ".bin")
        try {
            file.writeBytes(original)
            val part = JpegContainer.Part(0, original.size.toLong())
            val base = XiaomiPortraitTail.layout(original)
            for (mask in 0..7) {
                val options = ExportOptions(keepExif = mask and 1 != 0,
                    keepLocation = mask and 2 != 0, keepCaptureTime = mask and 4 != 0)
                val output = ByteArrayOutputStream()
                val filtered = XiaomiPortraitTail.writeFiltered(file, part, output, options)
                assertArrayEquals(filtered, output.toByteArray())
                assertEquals(original.size, filtered.size)
                assertEquals(base.secondEnd, XiaomiPortraitTail.layout(filtered).secondEnd)
                assertArrayEquals(original.copyOfRange(base.secondEnd, original.size),
                    filtered.copyOfRange(base.secondEnd, filtered.size))
                val text = filtered.toString(Charsets.ISO_8859_1)
                assertEquals(mask and 1 != 0 && mask and 4 != 0, "2026:08:02" in text)
                assertEquals(mask and 1 != 0, "Exif\u0000\u0000" in text)
                assertEquals(mask and 2 != 0, "GPSLatitude" in text)
                assertEquals(mask and 4 != 0, "CreateDate" in text)
            }
        } finally { file.delete() }
    }

    @Test fun unrecognizedDepthBoundaryIsRejected() {
        val altered = fixture()
        val marker = "MCBOKEHSOT".toByteArray(Charsets.US_ASCII)
        val offset = altered.indexOfSlice(marker)
        altered[offset] = 'X'.code.toByte()
        assertThrows(IllegalArgumentException::class.java) { XiaomiPortraitTail.layout(altered) }
    }

    private fun ByteArray.indexOfSlice(slice: ByteArray): Int =
        indices.first { at -> at + slice.size <= size && slice.indices.all { this[at + it] == slice[it] } }
}
