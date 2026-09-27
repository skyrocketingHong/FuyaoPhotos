package ing.fuyaoskyrocket.photoinfo.features.colors.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.GainmapDetails
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.PhotoColorInfo
import java.util.Locale
import ing.fuyaoskyrocket.photoinfo.platform.hasHdrPreviewContent

/** Decodes a [Bitmap] from [uri] using a software allocator so getPixel/getColor work. */
internal fun decodeBitmap(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(context.contentResolver, uri),
        ) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_DEFAULT
        }
    } else {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(BitmapFactory.decodeStream(input))
        }
    }
}

/** Extracts photo-level color information from [bitmap] for the parameter sheet. */
internal fun describePhoto(bitmap: Bitmap): PhotoColorInfo {
    val colorSpace = bitmap.colorSpace
    val colorModel = colorSpace?.getModel()
    val componentLabels = when (colorModel) {
        ColorSpace.Model.RGB -> listOf("R", "G", "B")
        else -> emptyList()
    }
    val componentRange = if (colorSpace != null && colorModel != null) {
        (0 until colorModel.getComponentCount()).joinToString(" · ") { index ->
            val label = componentLabels.getOrElse(index) { "C${index + 1}" }
            "$label ${formatFloat(colorSpace.getMinValue(index))}–${formatFloat(colorSpace.getMaxValue(index))}"
        }
    } else {
        "—"
    }
    val rgbColorSpace = colorSpace as? ColorSpace.Rgb
    val gainmapDetails = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && bitmap.hasGainmap()
    ) {
        readGainmapDetails(bitmap)
    } else {
        null
    }
    return PhotoColorInfo(
        width = bitmap.width,
        height = bitmap.height,
        bitmapConfig = bitmap.config?.toString() ?: "—",
        colorSpaceName = colorSpace?.getName() ?: "—",
        colorModel = colorModel?.toString() ?: "—",
        isWideGamut = colorSpace?.isWideGamut() == true,
        isSrgb = colorSpace?.isSrgb() == true,
        componentRange = componentRange,
        whitePoint = rgbColorSpace?.getWhitePoint()?.let(::formatCoordinatePair),
        primaries = rgbColorSpace?.getPrimaries()?.let(::formatPrimaries),
        hasGainmap = gainmapDetails != null,
        gainmap = gainmapDetails,
        hasHdrContent = bitmap.hasHdrPreviewContent(),
    )
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun readGainmapDetails(bitmap: Bitmap): GainmapDetails {
    val gainmap = requireNotNull(bitmap.getGainmap())
    return GainmapDetails(
        ratioMin = formatTriplet(gainmap.getRatioMin()),
        ratioMax = formatTriplet(gainmap.getRatioMax()),
        gamma = formatTriplet(gainmap.getGamma()),
        hdrTransitionRatio = formatFloat(gainmap.getMinDisplayRatioForHdrTransition()),
        fullHdrRatio = formatFloat(gainmap.getDisplayRatioForFullHdr()),
    )
}

private fun formatFloat(value: Float): String = String.format(Locale.US, "%.4f", value)

private fun formatTriplet(values: FloatArray): String =
    values.joinToString(", ") { formatFloat(it) }

private fun formatCoordinatePair(values: FloatArray): String =
    if (values.size >= 2) "x=${formatFloat(values[0])}, y=${formatFloat(values[1])}" else "—"

private fun formatPrimaries(values: FloatArray): String =
    if (values.size >= 6) {
        "R(${formatFloat(values[0])}, ${formatFloat(values[1])}) " +
            "G(${formatFloat(values[2])}, ${formatFloat(values[3])}) " +
            "B(${formatFloat(values[4])}, ${formatFloat(values[5])})"
    } else {
        "—"
    }
