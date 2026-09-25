package ing.fuyaoskyrocket.photoinfo.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Gainmap
import androidx.annotation.RequiresApi
import ing.fuyaoskyrocket.photoinfo.domain.media.HeifImageContainer
import java.io.File
import kotlin.math.pow

@RequiresApi(34)
internal object HeifGainmaps {
    fun attach(file: File, bitmap: Bitmap, sampleSize: Int) {
        val container = HeifImageContainer.read(file)
        // The editable-import policy caps HEIC at eight bits; ten-bit exports are this
        // helper's bread and butter, so only the structural invariants apply here.
        require(container.bitDepth in 8..12) { "Unsupported image precision" }
        val (id, metadata) = container.gainMap() ?: return
        val extracted = File.createTempFile("decode-gain-", if (container.avif) ".avif" else ".heic", file.parentFile)
        try {
            container.standalone(id).write(extracted)
            val pixels = requireNotNull(BitmapFactory.decodeFile(extracted.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize; inPreferredConfig = Bitmap.Config.ARGB_8888 }))
            fun List<Float>.channel(index: Int) = this[if (size == 1) 0 else index]
            bitmap.setGainmap(Gainmap(pixels).apply {
                setRatioMin(2f.pow(metadata.gainMinLog2.channel(0)), 2f.pow(metadata.gainMinLog2.channel(1)), 2f.pow(metadata.gainMinLog2.channel(2)))
                setRatioMax(2f.pow(metadata.gainMaxLog2.channel(0)), 2f.pow(metadata.gainMaxLog2.channel(1)), 2f.pow(metadata.gainMaxLog2.channel(2)))
                setGamma(1f / metadata.gamma.channel(0), 1f / metadata.gamma.channel(1), 1f / metadata.gamma.channel(2))
                setEpsilonSdr(metadata.offsetSdr.channel(0), metadata.offsetSdr.channel(1), metadata.offsetSdr.channel(2))
                setEpsilonHdr(metadata.offsetHdr.channel(0), metadata.offsetHdr.channel(1), metadata.offsetHdr.channel(2))
                setMinDisplayRatioForHdrTransition(2f.pow(metadata.capacityMinLog2))
                setDisplayRatioForFullHdr(2f.pow(metadata.capacityMaxLog2))
            })
        } finally { extracted.delete() }
    }
}
