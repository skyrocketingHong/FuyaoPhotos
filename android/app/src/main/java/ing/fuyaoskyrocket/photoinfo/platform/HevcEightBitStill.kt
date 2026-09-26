package ing.fuyaoskyrocket.photoinfo.platform

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import ing.fuyaoskyrocket.photoinfo.domain.media.HevcConfiguration
import java.nio.ByteBuffer

/**
 * Single-frame 8-bit HEVC still for mono auxiliary images - the Photographic Styles 3 part
 * mattes and the depth planes. HeifWriter tiles small planes on several firmwares, while
 * the native aux contract is one plain hvc1 item, so the frame goes through a byte-buffer
 * encoder session directly and comes back as an hvcC property plus a length-prefixed
 * payload, never through a muxer.
 */
object HevcEightBitStill {
    class Encoded(val hvcC: ByteArray, val payload: ByteArray)

    private const val SEMI_PLANAR = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
    private const val PLANAR = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar

    fun encodeBlack(width: Int, height: Int): Encoded = encodeMono(width, height, ByteArray(width * height))

    /** Encodes one full-range mono luma plane; chroma carries neutral 128 for 4:2:0 layouts. */
    fun encodeMono(width: Int, height: Int, luma: ByteArray): Encoded {
        require(width in 2..4096 && height in 2..4096 && width % 2 == 0 && height % 2 == 0) {
            "mono frame geometry $width×$height"
        }
        require(luma.size == width * height) { "mono plane size ${luma.size} for ${width}x$height" }
        val candidate = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder && !it.name.startsWith("OMX.") }
            .mapNotNull { info ->
                runCatching {
                    val capability = info.getCapabilitiesForType("video/hevc")
                    val level = capability.profileLevels
                        .filter { it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain }
                        .maxOfOrNull { it.level } ?: return@runCatching null
                    when {
                        SEMI_PLANAR in capability.colorFormats -> info.name to level to SEMI_PLANAR
                        PLANAR in capability.colorFormats -> info.name to level to PLANAR
                        else -> null
                    }
                }.getOrNull()
            }.firstOrNull() ?: error("This device exposes no 8-bit HEVC encoder for style mattes")

        val (identity, colorFormat) = candidate
        val format = MediaFormat.createVideoFormat("video/hevc", width, height).apply {
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
            // Several vendor firmwares refuse a profile-only configuration.
            setInteger(MediaFormat.KEY_LEVEL, identity.second)
            setInteger(MediaFormat.KEY_BIT_RATE, width * height * 2)
            // Rate control budgets bits per frame as bitrate/fps; a still is one frame.
            setInteger(MediaFormat.KEY_FRAME_RATE, 1)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL)
        }
        val units = MediaCodec.createByCodecName(identity.first).let { codec ->
            try {
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                val frame = monoFrame(width, height, luma, colorFormat)
                val frameSize = frame.remaining()
                val input = codec.dequeueInputBuffer(10_000_000)
                check(input >= 0) { "Matte encoder accepted no input buffer" }
                val buffer = codec.getInputBuffer(input)!!
                check(buffer.remaining() >= frameSize) {
                    "Matte encoder input holds ${buffer.remaining()} of $frameSize frame bytes"
                }
                buffer.clear()
                buffer.put(frame)
                codec.queueInputBuffer(input, 0, frameSize, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drain(codec)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        }
        val parameters = units.filter { it.first in 32..34 }.distinctBy { it.second.toList() }
        val slices = units.filter { it.first !in 32..34 }.distinctBy { it.second.toList() }
        check(parameters.any { it.first == 33 } && parameters.any { it.first == 34 }) {
            "Matte encoder stream lacks SPS/PPS: units=${units.map { it.first }}"
        }
        check(slices.isNotEmpty()) { "Matte encoder produced no coded slice" }
        // Apple's own depth and matte items declare monochrome in the record regardless
        // of the coded 4:2:0 layout; the reference converter does the same.
        return Encoded(HevcConfiguration.hvcBox(parameters, bitDepthMinus8 = 0, chromaFormatIdc = 0),
            HevcConfiguration.lengthPrefixed(slices))
    }

    /** Full-range mono 4:2:0 frame: the luma plane plus neutral chroma, in either layout. */
    private fun monoFrame(width: Int, height: Int, luma: ByteArray, colorFormat: Int): ByteBuffer {
        val lumaSize = width * height
        val chroma = lumaSize / 2
        val frame = ByteBuffer.allocate(lumaSize + chroma)
        frame.put(luma)
        if (colorFormat == PLANAR) {
            frame.put(ByteArray(chroma / 2))
            repeat(chroma / 2) { frame.put(128.toByte()) }
        } else {
            repeat(chroma / 2) { frame.put(0.toByte()); frame.put(128.toByte()) }
        }
        frame.rewind()
        return frame
    }

    private fun drain(codec: MediaCodec): List<Pair<Int, ByteArray>> {
        val units = ArrayList<Pair<Int, ByteArray>>()
        val info = MediaCodec.BufferInfo()
        var haveFormat = false
        while (true) {
            val index = codec.dequeueOutputBuffer(info, 10_000_000)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!haveFormat) { "Matte encoder format changed twice" }
                    haveFormat = true
                    val format = codec.outputFormat
                    for (number in 0 until 16) {
                        val key = "csd-$number"
                        if (!format.containsKey(key)) break
                        val buffer = format.getByteBuffer(key)!!
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        units += HevcConfiguration.splitAnnexB(bytes)
                    }
                }
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)!!
                    if (info.size > 0) {
                        val bytes = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.get(bytes)
                        // Qualcomm C2 delivers the VPS/SPS/PPS as flagged codec-config
                        // buffers instead of csd keys; both routes land in the same list.
                        units += HevcConfiguration.splitAnnexB(bytes)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
        check(haveFormat) { "Matte encoder produced no output format" }
        return units
    }
}
