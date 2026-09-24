package ing.fuyaoskyrocket.photoinfo.platform

import android.media.MediaCodecList
import android.os.Build
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportFormat

internal object ImageEncoderSupport {
    private val types by lazy {
        runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
            .flatMap { it.supportedTypes.toList() }.toSet() }.getOrDefault(emptySet())
    }
    fun supports(format: ExportFormat): Boolean = when(format) {
        ExportFormat.JPEG,ExportFormat.PNG -> true
        ExportFormat.HEIC -> Build.VERSION.SDK_INT>=28 && types.any { it in setOf("image/vnd.android.heic","video/hevc") }
        ExportFormat.AVIF -> Build.VERSION.SDK_INT>=34 && types.any { it in setOf("image/avif","video/av01") }
    }
}
