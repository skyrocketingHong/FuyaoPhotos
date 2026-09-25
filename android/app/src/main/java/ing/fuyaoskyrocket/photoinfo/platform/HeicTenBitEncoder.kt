package ing.fuyaoskyrocket.photoinfo.platform

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import ing.fuyaoskyrocket.photoinfo.domain.media.TenBitYuv
import java.io.File
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Ten-bit HEIC through MediaCodec + MediaMuxer. HeifWriter has no high-bit-depth input, so
 * the F16 plane is encoded by an HEVC Main10 encoder: directly as P010 buffers where the
 * encoder lists that input, otherwise through its input surface like the camera HDR path
 * (wide-gamut dataspace included). The platform HEIF muxer builds the container and takes
 * the Exif payload as a sample exactly like HeifWriter delivers it.
 */
object HeicTenBitEncoder {
    private class Candidate(val name: String, val level: Int, val p010: Boolean)

    fun encode(bitmap: Bitmap, destination: File, quality: Int, exif: ByteArray?, hdrTransfer: Boolean) {
        require(bitmap.config == Bitmap.Config.RGBA_F16) { "ten-bit encoder needs an F16 plane" }
        check(android.os.Build.VERSION.SDK_INT >= 33) { "Ten-bit HEIC encoding needs Android 13 or newer" }
        val widePq = hdrTransfer && TenBitYuv.transferFor(bitmap.colorSpace?.name.orEmpty()) == TenBitYuv.Transfer.PQ
        val wideHlg = hdrTransfer && TenBitYuv.transferFor(bitmap.colorSpace?.name.orEmpty()) != TenBitYuv.Transfer.PQ &&
            bitmap.colorSpace?.name?.contains("HLG") == true
        val candidates = pickEncoders()
        if (candidates.isEmpty()) error("This device exposes no HEVC Main10 encoder for ten-bit HEIC")
        var bufferFailure: Throwable? = null
        val p010 = candidates.firstOrNull { it.p010 }
        if (p010 != null) {
            try {
                encodeWithBuffers(bitmap, destination, quality, exif, p010, hdrTransfer, widePq)
                return
            } catch (failure: Throwable) {
                if (failure is OutOfMemoryError) throw failure
                // Buffer input is the fragile half of the encoder API; vendors list support
                // they later refuse at configure time, so record and try the surface route.
                bufferFailure = failure
            }
        }
        val surface = candidates.first()
        try {
            encodeThroughSurface(bitmap, destination, quality, exif, surface, widePq, wideHlg)
        } catch (failure: Throwable) {
            val bufferNote = if (p010 == null) "unavailable"
                else "${bufferFailure?.javaClass?.simpleName}: ${bufferFailure?.message}"
            throw IllegalStateException(
                "Ten-bit HEVC failed [p010 ${p010?.name ?: "none"}: $bufferNote] " +
                "[surface ${surface.name}: ${failure.javaClass.simpleName}: ${failure.message}]", failure)
        }
    }

    private fun baseFormat(bitmap: Bitmap, quality: Int, widePq: Boolean, level: Int): MediaFormat =
        MediaFormat.createVideoFormat("video/hevc", bitmap.width, bitmap.height).apply {
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10)
            // Several vendor firmwares refuse a profile-only configuration.
            setInteger(MediaFormat.KEY_LEVEL, level)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate(bitmap.width, bitmap.height, quality))
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0)
            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
            setInteger(MediaFormat.KEY_COLOR_TRANSFER,
                if (widePq) MediaFormat.COLOR_TRANSFER_ST2084 else MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL)
        }

    /**
     * Drains the codec and assembles the HEIF item ourselves: parameter sets become the hvcC
     * property, slices become the length-prefixed payload and the Exif rides as its own item.
     * The platform HEIF muxer silently falls back to MP4 on some firmwares, so nothing here
     * depends on MediaMuxer any more.
     */
    private fun encodeWithBuffers(bitmap: Bitmap, destination: File, quality: Int, exif: ByteArray?,
        candidate: Candidate, hdrTransfer: Boolean, widePq: Boolean) {
        val format = baseFormat(bitmap, quality, widePq, candidate.level).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010)
        }
        MediaCodec.createByCodecName(candidate.name).let { codec ->
            try {
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                var stride = if (codec.inputFormat.containsKey(MediaFormat.KEY_STRIDE))
                    codec.inputFormat.getInteger(MediaFormat.KEY_STRIDE) else bitmap.width
                if (stride >= bitmap.width * 2) stride /= 2
                val sliceHeight = if (codec.inputFormat.containsKey(MediaFormat.KEY_SLICE_HEIGHT))
                    codec.inputFormat.getInteger(MediaFormat.KEY_SLICE_HEIGHT) else bitmap.height
                val plane = TenBitYuv.encodeP010(readHalfBuffer(bitmap), bitmap.width, bitmap.height,
                    stride, sliceHeight, bitmap.colorSpace?.name.orEmpty(), hdrTransfer, sourceIsLinear(bitmap))
                val input = codec.dequeueInputBuffer(10_000_000)
                check(input >= 0) { "Encoder accepted no input buffer" }
                val inputBuffer = codec.getInputBuffer(input)!!
                check(inputBuffer.remaining() >= plane.size) {
                    "Encoder input buffer holds ${inputBuffer.remaining()} of ${plane.size} frame bytes"
                }
                inputBuffer.clear()
                inputBuffer.put(plane)
                codec.queueInputBuffer(input, 0, plane.size, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                writeAssembled(readCsdAndSlices(codec), bitmap, exif, destination)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        }
    }

    private fun encodeThroughSurface(bitmap: Bitmap, destination: File, quality: Int, exif: ByteArray?,
        candidate: Candidate, widePq: Boolean, wideHlg: Boolean) {
        val format = baseFormat(bitmap, quality, widePq, candidate.level)
        MediaCodec.createByCodecName(candidate.name).let { codec ->
            try {
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val surface = codec.createInputSurface()
                applyDataSpace(surface, widePq, wideHlg)
                codec.start()
                val canvas = surface.lockHardwareCanvas()
                try {
                    canvas.drawBitmap(bitmap, null, android.graphics.RectF(0f, 0f,
                        bitmap.width.toFloat(), bitmap.height.toFloat()), null)
                } finally { surface.unlockCanvasAndPost(canvas) }
                codec.signalEndOfInputStream()
                writeAssembled(readCsdAndSlices(codec), bitmap, exif, destination)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        }
    }

    private class CodedStream(val parameterSets: List<Pair<Int, ByteArray>>, val slices: List<Pair<Int, ByteArray>>)

    private fun readCsdAndSlices(codec: MediaCodec): CodedStream {
        val units = ArrayList<Pair<Int, ByteArray>>()
        val info = MediaCodec.BufferInfo()
        var haveFormat = false
        while (true) {
            val index = codec.dequeueOutputBuffer(info, 10_000_000)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!haveFormat) { "Encoder format changed twice" }
                    haveFormat = true
                    val format = codec.outputFormat
                    for (number in 0 until 16) {
                        val key = "csd-$number"
                        if (!format.containsKey(key)) break
                        val buffer = format.getByteBuffer(key)!!
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        units += ing.fuyaoskyrocket.photoinfo.domain.media.HevcConfiguration.splitAnnexB(bytes)
                    }
                }
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)!!
                    if (info.size > 0) {
                        val bytes = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.get(bytes)
                        // Qualcomm C2 delivers the VPS/SPS/PPS as flagged codec-config
                        // buffers instead of csd keys on the output format; both routes
                        // land in the same Annex-B unit list.
                        units += ing.fuyaoskyrocket.photoinfo.domain.media.HevcConfiguration.splitAnnexB(bytes)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
        check(haveFormat) { "Encoder produced no output format" }
        val parameters = units.filter { it.first in 32..34 }
        val slices = units.filter { it.first !in 32..34 }
        check(parameters.any { it.first == 33 } && parameters.any { it.first == 34 }) {
            "Encoder stream lacks SPS/PPS: units=${units.map { it.first }}, first=${units.firstOrNull()?.second
                ?.take(8)?.joinToString(" ") { (it.toInt() and 255).toString(16) }}"
        }
        check(slices.isNotEmpty()) { "Encoder produced no coded slice" }
        return CodedStream(parameters, slices)
    }

    private fun writeAssembled(stream: CodedStream, bitmap: Bitmap, exif: ByteArray?, destination: File) {
        val hvcC = ing.fuyaoskyrocket.photoinfo.domain.media.HevcConfiguration.hvcBox(stream.parameterSets)
        val payload = ing.fuyaoskyrocket.photoinfo.domain.media.HevcConfiguration.lengthPrefixed(stream.slices)
        val ispe = IsoBmffFullProperty.ispe(bitmap.width, bitmap.height)
        val pixi = IsoBmffFullProperty.pixi(intArrayOf(10, 10, 10))
        val item = ing.fuyaoskyrocket.photoinfo.domain.media.HeifImageContainer.Item(
            1, "hvc1", byteArrayOf(0), payload,
            listOf(
                ing.fuyaoskyrocket.photoinfo.domain.media.HeifImageContainer.Property(1, true),
                ing.fuyaoskyrocket.photoinfo.domain.media.HeifImageContainer.Property(2, false),
                ing.fuyaoskyrocket.photoinfo.domain.media.HeifImageContainer.Property(3, true)))
        val container = ing.fuyaoskyrocket.photoinfo.domain.media.HeifImageContainer(
            false, 1, listOf(item), listOf(ispe, pixi, hvcC), emptyList())
        container.withExif(exif).write(destination)
    }

    // Small full-box property builders kept next to their only consumer.
    private object IsoBmffFullProperty {
        fun ispe(width: Int, height: Int): ByteArray = box("ispe") {
            writeInt(width); writeInt(height)
        }
        fun pixi(channels: IntArray): ByteArray = box("pixi") {
            write(channels.size); channels.forEach(::write)
        }
        private fun box(type: String, body: java.io.DataOutputStream.() -> Unit): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val stream = java.io.DataOutputStream(out)
            stream.writeInt(0) // placeholder
            stream.writeBytes(type)
            stream.writeInt(0) // full box version and flags
            stream.body()
            stream.flush()
            val bytes = out.toByteArray()
            bytes[0] = (bytes.size ushr 24).toByte(); bytes[1] = (bytes.size ushr 16).toByte()
            bytes[2] = (bytes.size ushr 8).toByte(); bytes[3] = bytes.size.toByte()
            return bytes
        }
    }

    // DataSpace.BT2020 / BT2020_PQ / BT2020_HLG with full range, read from the SDK constants.
    private const val DATASPACE_BT2020 = 147193856
    private const val DATASPACE_BT2020_PQ = 163971072
    private const val DATASPACE_BT2020_HLG = 168165376

    private fun applyDataSpace(surface: Surface, widePq: Boolean, wideHlg: Boolean) {
        val value = when {
            widePq -> DATASPACE_BT2020_PQ
            wideHlg -> DATASPACE_BT2020_HLG
            else -> DATASPACE_BT2020
        }
        runCatching {
            val setter: Method = Surface::class.java.getMethod("setBuffersDataSpace", Int::class.javaPrimitiveType)
            setter.invoke(surface, value)
        }
    }

    /**
     * F16 storage follows the bitmap colour space's transfer curve: scRGB-family spaces are
     * linear, an sRGB/gamma-tagged F16 plane already carries encoded values that must pass
     * through (or be linearised for HDR targets) instead of receiving a second OETF.
     */
    private fun sourceIsLinear(bitmap: Bitmap): Boolean {
        val space = bitmap.colorSpace ?: return true
        val name = space.name
        if (name.contains("linear", true) || name.contains("scRGB", true)) return true
        val rgb = space as? android.graphics.ColorSpace.Rgb ?: return true
        // The gamma exponent lives in the transfer parameter field g.
        val parameters = rgb.transferParameters ?: return true
        return parameters.g == 1.0
    }

    private fun readHalfBuffer(bitmap: Bitmap): java.nio.ShortBuffer {
        val buffer = ByteBuffer.allocate(bitmap.byteCount).order(ByteOrder.nativeOrder())
        bitmap.copyPixelsToBuffer(buffer)
        // copyPixelsToBuffer leaves the position at the end; the short view would be empty.
        buffer.rewind()
        return buffer.asShortBuffer()
    }

    /** Bits per pixel scales with quality; at 100 the single frame lands near visually lossless. */
    private fun bitrate(width: Int, height: Int, quality: Int): Int {
        val bitsPerPixel = quality.coerceIn(1, 100) * 0.025f
        return (width.toLong() * height * bitsPerPixel.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** P010-capable encoders first, then any Main10 encoder, each with a reported level. */
    private fun pickEncoders(): List<Candidate> = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filter { it.isEncoder && !it.name.startsWith("OMX.") }
        .mapNotNull { info ->
            runCatching {
                val capability = info.getCapabilitiesForType("video/hevc")
                val level = capability.profileLevels
                    .firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 }
                    ?.level ?: return@runCatching null
                Candidate(info.name, level,
                    capability.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010))
            }.getOrNull()
        }
        .sortedByDescending { it.p010 }
}
