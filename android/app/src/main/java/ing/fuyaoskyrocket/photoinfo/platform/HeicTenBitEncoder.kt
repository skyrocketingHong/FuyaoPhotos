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
    fun encode(bitmap: Bitmap, destination: File, quality: Int, exif: ByteArray?, hdrTransfer: Boolean) {
        require(bitmap.config == Bitmap.Config.RGBA_F16)
        check(android.os.Build.VERSION.SDK_INT >= 33) { "Ten-bit HEIC encoding needs Android 13 or newer" }
        val widePq = hdrTransfer && TenBitYuv.transferFor(bitmap.colorSpace?.name.orEmpty()) == TenBitYuv.Transfer.PQ
        val wideHlg = hdrTransfer && TenBitYuv.transferFor(bitmap.colorSpace?.name.orEmpty()) != TenBitYuv.Transfer.PQ &&
            bitmap.colorSpace?.name?.contains("HLG") == true
        val p010Name = pickEncoder(preferP010 = true)
        if (p010Name != null) {
            try {
                encodeWithBuffers(bitmap, destination, quality, exif, p010Name, hdrTransfer, widePq)
                return
            } catch (failure: Throwable) {
                if (failure is OutOfMemoryError) throw failure
                // Fall through to the surface path; buffer input is the fragile half of the
                // encoder API and vendors list support they later refuse at configure time.
            }
        }
        val surfaceName = pickEncoder(preferP010 = false)
            ?: error("This device exposes no HEVC Main10 encoder for ten-bit HEIC")
        encodeThroughSurface(bitmap, destination, quality, exif, surfaceName, widePq, wideHlg)
    }

    private fun baseFormat(bitmap: Bitmap, quality: Int, widePq: Boolean): MediaFormat =
        MediaFormat.createVideoFormat("video/hevc", bitmap.width, bitmap.height).apply {
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate(bitmap.width, bitmap.height, quality))
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0)
            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
            setInteger(MediaFormat.KEY_COLOR_TRANSFER,
                if (widePq) MediaFormat.COLOR_TRANSFER_ST2084 else MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL)
        }

    private fun encodeWithBuffers(bitmap: Bitmap, destination: File, quality: Int, exif: ByteArray?,
        codecName: String, hdrTransfer: Boolean, widePq: Boolean) {
        val format = baseFormat(bitmap, quality, widePq).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010)
        }
        val codec = MediaCodec.createByCodecName(codecName)
        val muxer = MediaMuxer(destination.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_HEIF)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val stride = if (codec.inputFormat.containsKey(MediaFormat.KEY_STRIDE))
                codec.inputFormat.getInteger(MediaFormat.KEY_STRIDE) else bitmap.width
            val sliceHeight = if (codec.inputFormat.containsKey(MediaFormat.KEY_SLICE_HEIGHT))
                codec.inputFormat.getInteger(MediaFormat.KEY_SLICE_HEIGHT) else bitmap.height
            val plane = TenBitYuv.encodeP010(readHalfBuffer(bitmap), bitmap.width, bitmap.height,
                stride, sliceHeight, bitmap.colorSpace?.name.orEmpty(), hdrTransfer)
            var track = -1
            var started = false
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()
            while (!outputDone) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10_000_000)
                    if (index >= 0) {
                        codec.getInputBuffer(index)!!.apply {
                            clear()
                            put(plane)
                        }
                        codec.queueInputBuffer(index, 0, plane.size, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    }
                }
                val index = codec.dequeueOutputBuffer(info, 10_000_000)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!started) { "Encoder format changed after the muxer started" }
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                        writeExif(muxer, track, exif)
                    }
                    index >= 0 -> {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && started) {
                            muxer.writeSampleData(track, codec.getOutputBuffer(index)!!, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            check(started) { "Encoder produced no output format" }
            muxer.stop()
        } finally {
            muxer.release()
            runCatching { codec.stop() }
            codec.release()
        }
    }

    /**
     * The camera HDR route: the encoder consumes frames from its input surface, tagged with
     * a BT.2020 dataspace. setBuffersDataSpace is not public but its signature and the
     * dataspace values are stable, and this is the one hook that reaches it.
     */
    private fun encodeThroughSurface(bitmap: Bitmap, destination: File, quality: Int, exif: ByteArray?,
        codecName: String, widePq: Boolean, wideHlg: Boolean) {
        val format = baseFormat(bitmap, quality, widePq)
        val codec = MediaCodec.createByCodecName(codecName)
        val muxer = MediaMuxer(destination.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_HEIF)
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
            var track = -1
            var started = false
            val info = MediaCodec.BufferInfo()
            while (true) {
                val index = codec.dequeueOutputBuffer(info, 10_000_000)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!started) { "Encoder format changed after the muxer started" }
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                        writeExif(muxer, track, exif)
                    }
                    index >= 0 -> {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && started) {
                            muxer.writeSampleData(track, codec.getOutputBuffer(index)!!, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            check(started) { "Encoder produced no output format" }
            muxer.stop()
        } finally {
            muxer.release()
            runCatching { codec.stop() }
            codec.release()
        }
    }

    private fun writeExif(muxer: MediaMuxer, track: Int, exif: ByteArray?) {
        exif?.let { bytes ->
            val payload = ByteBuffer.allocateDirect(bytes.size)
            payload.put(bytes)
            payload.flip()
            val info = MediaCodec.BufferInfo()
            info.set(0, bytes.size, 0, 0)
            muxer.writeSampleData(track, payload, info)
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

    private fun readHalfBuffer(bitmap: Bitmap): java.nio.ShortBuffer {
        val buffer = ByteBuffer.allocate(bitmap.byteCount).order(ByteOrder.nativeOrder())
        bitmap.copyPixelsToBuffer(buffer)
        return buffer.asShortBuffer()
    }

    /** Bits per pixel scales with quality; at 100 the single frame lands near visually lossless. */
    private fun bitrate(width: Int, height: Int, quality: Int): Int {
        val bitsPerPixel = quality.coerceIn(1, 100) * 0.025f
        return (width.toLong() * height * bitsPerPixel.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun pickEncoder(preferP010: Boolean): String? = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filter { it.isEncoder && !it.name.startsWith("OMX.") }
        .firstOrNull { info ->
            runCatching {
                val capability = info.getCapabilitiesForType("video/hevc")
                val main10 = capability.profileLevels.any {
                    it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                }
                main10 && (!preferP010 || capability.colorFormats.contains(
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010))
            }.getOrDefault(false)
        }?.name
}
