package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.HevcConfiguration
import org.junit.Assert.*
import org.junit.Test

class HevcConfigurationTest {
    private fun nal(type: Int, extra: Int): ByteArray {
        // first byte: forbidden(0) + type(6) + layer high bit; second byte: layer + id
        val head = byteArrayOf(((type and 0x3f) shl 1).toByte(), 1)
        return head + ByteArray(extra) { (it % 251).toByte() }
    }

    private fun annexB(vararg nals: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (nal in nals) {
            out.write(byteArrayOf(0, 0, 0, 1))
            out.write(nal)
        }
        return out.toByteArray()
    }

    @Test fun annexBSplitKeepsTypesAndPayloads() {
        val vps = nal(32, 20); val sps = nal(33, 40); val pps = nal(34, 10); val slice = nal(19, 60)
        val units = HevcConfiguration.splitAnnexB(annexB(vps, sps, pps, slice))
        assertEquals(listOf(32, 33, 34, 19), units.map { it.first })
        assertArrayEquals(vps, units[0].second)
        assertArrayEquals(slice, units[3].second)
    }

    @Test fun hvcBoxCarriesProfileAndArrays() {
        val vps = nal(32, 16)
        // NAL header, sub-layer byte, then the twelve profile tier level bytes.
        val sps = byteArrayOf(0x42, 1, 0, 1, 1, 0x60, 0, 0, 0, 0, 0x90.toByte(), 0, 0, 0, 0) + ByteArray(24)
        val pps = nal(34, 8)
        val box = HevcConfiguration.hvcBox(listOf(32 to vps, 33 to sps, 34 to pps))
        assertEquals(box.size, ((box[0].toInt() and 255) shl 24) or ((box[1].toInt() and 255) shl 16) or
            ((box[2].toInt() and 255) shl 8) or (box[3].toInt() and 255))
        assertEquals("hvcC", String(box, 4, 4, Charsets.US_ASCII))
        assertEquals(1, box[8].toInt())
        assertEquals(1, box[9].toInt()) // profile idc copied from SPS
        // box[9+i] copies SPS payload byte i: 0x60 at 11, 0x90 at 16.
        assertEquals(0x60, box[11].toInt() and 255)
        assertEquals(0x90, box[16].toInt() and 255)
        // three arrays: VPS, SPS, PPS
        assertEquals(3, box[30].toInt())
        val slice = nal(19, 30)
        val lengthPrefixed = HevcConfiguration.lengthPrefixed(listOf(19 to slice))
        assertEquals(slice.size + 4, lengthPrefixed.size)
        assertEquals(slice.size, ((lengthPrefixed[0].toInt() and 255) shl 24) or ((lengthPrefixed[1].toInt() and 255) shl 16) or
            ((lengthPrefixed[2].toInt() and 255) shl 8) or (lengthPrefixed[3].toInt() and 255))
    }
}
