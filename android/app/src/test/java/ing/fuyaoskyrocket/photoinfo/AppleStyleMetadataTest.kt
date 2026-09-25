package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.AppleStyleMetadata
import ing.fuyaoskyrocket.photoinfo.domain.media.HeifImageContainer
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class AppleStyleMetadataTest {
    @Test fun identityStyleDataKeepsTheVerifiedLatticeLayout() {
        val data = AppleStyleMetadata.identityStyleData()
        assertEquals(51840, data.size)
        repeat(864) { block ->
            for (index in 0 until 30) {
                val value = ((data[(block * 30 + index) * 2 + 1].toInt() and 255) shl 8) or
                    (data[(block * 30 + index) * 2].toInt() and 255)
                if (index == 3 || index == 7 || index == 11) assertEquals(0x3c00, value) else assertEquals(0, value)
            }
        }
    }

    @Test fun styleMetadataCarriesTheGoldenContract() {
        val payload = AppleStyleMetadata.styleMetadata()
        assertEquals("bplist00", String(payload, 0, 8, Charsets.US_ASCII))
        val trailer = payload.size - 32
        val offsetSize = payload[trailer + 6].toInt()
        val refSize = payload[trailer + 7].toInt()
        val count = ByteBuffer.wrap(payload, trailer + 8, 8).long.toInt()
        val tableAt = ByteBuffer.wrap(payload, trailer + 24, 8).long.toInt()
        val topObject = run {
            var value = 0L
            repeat(8) { value = (value shl 8) or (payload[trailer + 16 + it].toLong() and 255) }
            value.toInt()
        }
        val topOffset = run {
            var value = 0L
            repeat(offsetSize) { value = (value shl 8) or (payload[tableAt + topObject * offsetSize + it].toLong() and 255) }
            value.toInt()
        }
        assertTrue(offsetSize in intArrayOf(1, 2, 4) && refSize in intArrayOf(1, 2) && count > 17)
        fun unsigned(at: Int, size: Int): Long {
            var value = 0L
            repeat(size) { value = (value shl 8) or (payload[at + it].toLong() and 255) }
            return value
        }
        val dataLengths = mutableSetOf<Int>()
        repeat(count) { index ->
            val at = unsigned(tableAt + index * offsetSize, offsetSize).toInt()
            when (payload[at].toInt() and 0xf0) {
                0x40 -> {
                    var header = 1
                    var length = payload[at].toInt() and 15
                    if (length == 15) {
                        val marker = payload[at + 1].toInt()
                        length = unsigned(at + 2, 1 shl (marker and 0x0f)).toInt()
                        header = 2 + (1 shl (marker and 0x0f))
                    }
                    if (at + header + length <= tableAt) dataLengths += length
                }
                0xd0 -> {
                    var header = 1
                    var length = payload[at].toInt() and 15
                    if (length == 15) {
                        val marker = payload[at + 1].toInt()
                        length = unsigned(at + 2, 1 shl (marker and 0x0f)).toInt()
                        header = 2 + (1 shl (marker and 0x0f))
                    }
                    if (at == topOffset) assertEquals(17, length)
                    else assertTrue(length in intArrayOf(3, 9, 10))
                }
            }
        }
        // The style lattice is the payload Photos keys on; golden light maps and GTC ride along.
        assertTrue(51840 in dataLengths)
        assertTrue(2048 in dataLengths)
        assertTrue(516 in dataLengths)
    }

    @Test fun stylesNoteKeepsAppleEntryLayout() {
        val identifier = "00112233-4455-6677-8899-aabbccddeeff"
        val note = AppleStyleMetadata.stylesNote(identifier, includePortrait = false)
        assertEquals(16 + 2 * 12 + 4 + 37 + 91, note.size)
        assertEquals("Apple iOS", String(note, 0, 9, Charsets.US_ASCII))
        assertEquals(0, note[9].toInt()); assertEquals(0, note[10].toInt()); assertEquals(1, note[11].toInt())
        assertEquals('M'.code, note[12].toInt()); assertEquals('M'.code, note[13].toInt())
        fun u16(at: Int) = ((note[at].toInt() and 255) shl 8) or (note[at + 1].toInt() and 255)
        fun u32(at: Int) = ((note[at].toLong() and 255) shl 24) or ((note[at + 1].toLong() and 255) shl 16) or
            ((note[at + 2].toLong() and 255) shl 8) or (note[at + 3].toLong() and 255)
        assertEquals(2, u16(14))
        assertEquals(43, u16(16)); assertEquals(2, u16(18)); assertEquals(37L, u32(20)); assertEquals(44L, u32(24))
        assertEquals(84, u16(28)); assertEquals(7, u16(30)); assertEquals(91L, u32(32)); assertEquals(81L, u32(36))
        assertEquals(identifier.uppercase(), String(note, 44, 36, Charsets.US_ASCII))
        assertEquals(0, note[80].toInt())
        assertArrayEquals(AppleStyleMetadata.TAG_84, note.copyOfRange(81, 81 + 91))

        val combined = AppleStyleMetadata.stylesNote(identifier, includePortrait = true)
        assertEquals(16 + 3 * 12 + 4 + 37 + 91, combined.size)
        assertEquals(3, u16At(combined, 14))
        assertEquals(0x14, u16At(combined, 16))
        assertEquals(43, u16At(combined, 28))

        val all = AppleStyleMetadata.appleNote("00112233-4455-6677-8899-aabbccddeeff", true, identifier)
        assertEquals(16 + 4 * 12 + 4 + 37 + 37 + 91, all.size)
        assertEquals(4, u16At(all, 14))
        assertEquals(17, u16At(all, 16))
        assertEquals(0x14, u16At(all, 28))
        assertEquals(43, u16At(all, 40))
        assertEquals(84, u16At(all, 52))
    }

    private fun u16At(bytes: ByteArray, at: Int) = ((bytes[at].toInt() and 255) shl 8) or (bytes[at + 1].toInt() and 255)

    @Test fun photographicStylesLayerAttachesToTheContainer() {
        val base = HeifImageContainer.read(fixture("base"))
        val styled = base.withPhotographicStyles(2880, 2470, true, null, null)
        val style = styled.items.single { it.type == "uri " }
        val declaration = String(style.infoSuffix, Charsets.ISO_8859_1)
        assertTrue(declaration.startsWith("metadata\u0000tag:apple.com,2023:photo:metadata:styles\u0000"))
        assertTrue(style.payload.size > 51840)
        assertTrue(style.hidden)
        assertEquals(listOf(base.primary), styled.references.single { it.type == "cdsc" && it.from == style.id }.to)

        fun auxiliaryType(item: HeifImageContainer.Item): String? = item.properties
            .mapNotNull { property ->
                val raw = styled.properties[property.index - 1]
                if (String(raw, 4, 4, Charsets.US_ASCII) == "auxC")
                    String(raw, 12, raw.size - 12, Charsets.US_ASCII).trimEnd('\u0000') else null
            }.singleOrNull()
        val grid = styled.items.single { auxiliaryType(it) == AppleStyleMetadata.DELTA_MAP_URN }
        assertEquals(8, grid.payload.size)
        val tiles = styled.references.single { it.type == "dimg" && it.from == grid.id }.to
        assertEquals(30, tiles.size)
        tiles.forEach { id ->
            val tile = styled.items.single { it.id == id }
            assertEquals("hvc1", tile.type)
            assertArrayEquals(AppleStyleMetadata.DELTA_TILE, tile.payload)
        }
        assertEquals(listOf(base.primary), styled.references.single { it.type == "auxl" && it.from == grid.id }.to)

        val file = File.createTempFile("style-layer", ".heic")
        try {
            styled.write(file)
            val bytes = file.readBytes()
            assertEquals("ftyp", String(bytes, 4, 4, Charsets.ISO_8859_1))
            // The declaration, the style plist and the tiles are all present in the written file.
            val text = String(bytes, Charsets.ISO_8859_1)
            assertTrue(text.contains("tag:apple.com,2023:photo:metadata:styles"))
            assertTrue(text.contains("bplist00"))
            assertTrue(bytes.size > styled.items.sumOf { it.payload.size } + 8)
        } finally { file.delete() }
    }

    private fun fixture(name: String) = requireNotNull(javaClass.getResourceAsStream("/media/$name.heic")).use { it.readBytes() }
}
