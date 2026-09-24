package ing.fuyaoskyrocket.photoinfo.platform

import android.graphics.Bitmap
import android.graphics.Gainmap
import android.os.Build
import androidx.annotation.RequiresApi
import ing.fuyaoskyrocket.photoinfo.domain.media.IsoGainMapMetadata

@RequiresApi(34)
internal object AndroidGainMapMetadata {
    fun normalizeBasePrimaries(gainmap: Gainmap, base: android.graphics.ColorSpace?) {
        if(Build.VERSION.SDK_INT<36)return
        val alternative=gainmap.alternativeImagePrimaries as? android.graphics.ColorSpace.Rgb ?: return
        val rgb=base as? android.graphics.ColorSpace.Rgb ?: return
        fun same(a:FloatArray,b:FloatArray)=a.size==b.size && a.indices.all { kotlin.math.abs(a[it]-b[it])<.0001f }
        if(same(alternative.primaries,rgb.primaries) && same(alternative.whitePoint,rgb.whitePoint))gainmap.alternativeImagePrimaries=null
    }
    fun read(gainmap: Gainmap): IsoGainMapMetadata {
        if (Build.VERSION.SDK_INT >= 36) {
            require(gainmap.gainmapDirection == Gainmap.GAINMAP_DIRECTION_SDR_TO_HDR &&
                gainmap.alternativeImagePrimaries == null) { "Unsupported gain-map rendition" }
        }
        val metadata = IsoGainMapMetadata.fromRatios(
            gainmap.ratioMin, gainmap.ratioMax, gainmap.gamma,
            gainmap.epsilonSdr, gainmap.epsilonHdr,
            gainmap.minDisplayRatioForHdrTransition, gainmap.displayRatioForFullHdr,
        )
        if (gainmap.gainmapContents.config == Bitmap.Config.ALPHA_8) {
            require(metadata.channelCount == 1) { "Single-plane gain map has different channel metadata" }
        }
        return metadata
    }
}
