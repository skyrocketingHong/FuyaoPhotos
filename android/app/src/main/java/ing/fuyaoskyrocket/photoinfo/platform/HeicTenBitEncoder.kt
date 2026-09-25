package ing.fuyaoskyrocket.photoinfo.platform

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import ing.fuyaoskyrocket.photoinfo.domain.media.TenBitYuv
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Ten-bit HEIC through MediaCodec + MediaMuxer. HeifWriter has no high-bit-depth input, so
 * the F16 plane becomes P010 for an HEVC Main10 encoder and the platform's HEIF muxer builds
 * the container; the Exif payload rides along as a sample exactly like HeifWriter sends it.
 */
object HeicTenBitEncoder {
    fun encode(bitmap: Bitmap, destination: File, quality: Int, exif: ByteArray?) {
        require(bitmap.config == Bitmap.Config.RGBA_F16)
        check(android.os.Build.VERSION.SDK_INT >= 33) { "Ten-bit HEIC encoding needs Android 13 or newer" }
        val codecName = pickEncoder()
            ?: error("No HEVC Main10 encoder accepts 10-bit P010 input on this device")
        val format = MediaFormat.createVideoFormat("video/hevc", bitmap.width, bitmap.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate(bitmap.width, bitmap.height, quality))
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0)
            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
            setInteger(
                MediaFormat.KEY_COLOR_TRANSFER,
                if (TenBitYuv.transferFor(bitmap.colorSpace?.name.orEmpty()) == TenBitYuv.Transfer.PQ)
                    MediaFormat.COLOR_TRANSFER_ST2084 else MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL)
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
                stride, sliceHeight, bitmap.colorSpace?.name.orEmpty())
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
                        exif?.let { bytes ->
                            val payload = ByteBuffer.allocateDirect(bytes.size)
                            payload.put(bytes)
                            payload.flip()
                            val exifInfo = MediaCodec.BufferInfo()
                            exifInfo.set(0, bytes.size, 0, 0)
                            muxer.writeSampleData(track, payload, exifInfo)
                        }
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

    /** One pixel copy only: the F16 plane is read straight into P010 through this view. */
    private fun readHalfBuffer(bitmap: Bitmap): java.nio.ShortBuffer {
        val buffer = ByteBuffer.allocateDirect(bitmap.byteCount).order(ByteOrder.nativeOrder())
        bitmap.copyPixelsToBuffer(buffer)
        return buffer.asShortBuffer()
    }

    /** Bits per pixel scales with quality; at 100 the single frame lands near visually lossless. */
    private fun bitrate(width: Int, height: Int, quality: Int): Int {
        val bitsPerPixel = quality.coerceIn(1, 100) * 0.025f
        return (width.toLong() * height * bitsPerPixel.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun pickEncoder(): String? = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filter { it.isEncoder && !it.name.startsWith("OMX.") }
        .firstOrNull { info ->
            runCatching {
                val capability = info.getCapabilitiesForType("video/hevc")
                capability.profileLevels.any {
                    it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                } && capability.colorFormats.contains(
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010)
            }.getOrDefault(false)
        }?.name
}
