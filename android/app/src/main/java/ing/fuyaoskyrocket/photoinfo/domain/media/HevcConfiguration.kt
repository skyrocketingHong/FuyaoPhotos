package ing.fuyaoskyrocket.photoinfo.domain.media

/**
 * Assembles an HEVC item from an encoder session: parameter sets become the hvcC property
 * and the coded slices become the length-prefixed item payload, independent of any muxer.
 */
internal object HevcConfiguration {
    /** Level 3.0, the value Apple's aux items and the reference converter use. */
    private const val LEVEL_FALLBACK = 0x5A
    private class Nal(val type: Int, val payload: ByteArray)

    /** Splits an Annex-B byte stream (00 00 01 / 00 00 00 01 start codes) into NAL units. */
    fun splitAnnexB(stream: ByteArray): List<Pair<Int, ByteArray>> {
        val payloadStarts = ArrayList<Int>()
        val codeStarts = ArrayList<Int>()
        var at = 0
        while (at + 3 <= stream.size) {
            if (stream[at] == 0.toByte() && stream[at + 1] == 0.toByte() && stream[at + 2] == 1.toByte()) {
                codeStarts += at; payloadStarts += at + 3; at += 3
            } else if (at + 4 <= stream.size && stream[at] == 0.toByte() && stream[at + 1] == 0.toByte() &&
                stream[at + 2] == 0.toByte() && stream[at + 3] == 1.toByte()) {
                codeStarts += at; payloadStarts += at + 4; at += 4
            } else at++
        }
        val units = ArrayList<Pair<Int, ByteArray>>()
        for (index in payloadStarts.indices) {
            val from = payloadStarts[index]
            val to = if (index + 1 < codeStarts.size) codeStarts[index + 1] else stream.size
            if (to > from + 2) {
                val payload = stream.copyOfRange(from, to)
                units += ((payload[0].toInt() and 0x7e) shr 1) to payload
            }
        }
        return units
    }

    /**
     * Builds the HEVCDecoderConfigurationRecord hvcC box from VPS/SPS/PPS NALs. The profile
     * tier and level bytes copy straight out of the SPS, which is valid for single-layer
     * streams without sub-layer extensions; [bitDepthMinus8] covers 8-bit aux planes
     * alongside the ten-bit base images, [chromaFormatIdc] declares mono (0) aux planes
     * like Apple's own depth and matte items.
     */
    fun hvcBox(parameterSets: List<Pair<Int, ByteArray>>, bitDepthMinus8: Int = 2,
        chromaFormatIdc: Int = 1): ByteArray {
        val sps = parameterSets.first { it.first == 33 }.second
        // Two NAL header bytes precede the sequence payload; byte 2 carries the sub-layer
        // flags and bytes 3..15 hold the profile tier level copied into the record.
        require(sps.size >= 15) { "sps too short for hvcC" }
        require(((sps[2].toInt() shr 1) and 7) == 0) { "sps sub-layer extensions unsupported" }
        require(bitDepthMinus8 in 0..4) { "hvcC bit depth" }
        require(chromaFormatIdc in 0..3) { "hvcC chroma format" }
        val record = java.io.ByteArrayOutputStream()
        fun u16(value: Int) { record.write(value shr 8); record.write(value) }
        fun u32(value: Int) {
            record.write(value ushr 24); record.write(value ushr 16); record.write(value ushr 8); record.write(value)
        }
        u32(0) // placeholder for box size, patched below
        record.write("hvcC".toByteArray(Charsets.US_ASCII))
        record.write(1) // configurationVersion
        // The record's profile tier level keeps the SPS's 48-bit constraints and level but
        // only the TOP 24 compatibility bits - Apple's writer sizes the field that way and
        // a 32-bit copy shifts every following field one byte past its spec offset.
        record.write(sps[3].toInt() and 255)
        for (index in 4..6) record.write(sps[index].toInt() and 255)
        for (index in 8..13) record.write(sps[index].toInt() and 255)
        val level = sps[14].toInt() and 255
        // Vendor encoders emit level_idc 0 when unconstrained; level zero is reserved.
        record.write(if (level == 0) LEVEL_FALLBACK else level)
        u16(0xF000) // min_spatial_segmentation_idc with reserved bits
        record.write(0xFC) // parallelismType with reserved bits
        record.write(0xFC or chromaFormatIdc) // chromaFormat with reserved bits
        record.write(0xF8 or bitDepthMinus8) // bitDepthLumaMinus8 with reserved bits
        record.write(0xF8 or bitDepthMinus8) // bitDepthChromaMinus8 with reserved bits
        u16(0) // avgFrameRate
        record.write(0x0B) // one temporal layer, four-byte NAL lengths
        val arrays = parameterSets.groupBy({ it.first }, { it.second })
        record.write(arrays.size)
        for ((type, nals) in arrays) {
            record.write(0x80 or type) // complete array of this NAL type
            u16(nals.size)
            for (nal in nals) { u16(nal.size); record.write(nal) }
        }
        val box = record.toByteArray()
        box[0] = (box.size ushr 24).toByte(); box[1] = (box.size ushr 16).toByte()
        box[2] = (box.size ushr 8).toByte(); box[3] = box.size.toByte()
        return box
    }

    /** Four-byte length prefixes for the coded slices that form the image item payload. */
    fun lengthPrefixed(slices: List<Pair<Int, ByteArray>>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for ((_, nal) in slices) {
            out.write(nal.size ushr 24); out.write(nal.size ushr 16)
            out.write(nal.size ushr 8); out.write(nal.size)
            out.write(nal)
        }
        return out.toByteArray()
    }
}
