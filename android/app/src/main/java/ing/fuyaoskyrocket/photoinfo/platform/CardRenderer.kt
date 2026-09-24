package ing.fuyaoskyrocket.photoinfo.platform

import android.os.Build
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import ing.fuyaoskyrocket.photoinfo.domain.layout.CardLayout
import ing.fuyaoskyrocket.photoinfo.domain.layout.CardLayoutEngine
import ing.fuyaoskyrocket.photoinfo.domain.model.CardStyle
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoInfo
import ing.fuyaoskyrocket.photoinfo.domain.render.BoxBlur
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** The preview and full-resolution exporter share this exact renderer. No screen capture is exported. */
class CardRenderer {
    fun preview(source: Bitmap, info: PhotoInfo, style: CardStyle, typography: CardTypography): Bitmap {
        val copy = checkNotNull(source.copy(if(source.config==Bitmap.Config.RGBA_F16)Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888, true))
        if (Build.VERSION.SDK_INT >= 34 && source.hasGainmap()) copy.setGainmap(source.gainmap)
        try { drawInPlace(copy, info, style, typography); return copy }
        catch (failure: Throwable) { copy.recycle(); throw failure }
    }

    fun drawInPlace(target: Bitmap, info: PhotoInfo, style: CardStyle, typography: CardTypography, opaqueBackground: Boolean = false) {
        require(target.isMutable) { "Renderer requires a mutable bitmap" }
        val s = style.sanitized()
        val referenceText = CardTextRenderer(typography, CardLayoutEngine.FONT_SIZE)
        val layout = CardLayoutEngine.layout(target.width, target.height, info, s, referenceText::measure)
        if (layout == null && !opaqueBackground) return
        val gainmap = if (Build.VERSION.SDK_INT >= 34) target.gainmap else null
        // Android 15+ Canvas(Bitmap) clears the gainmap; detach explicitly on 14
        // as well. Finish every base-image draw before attaching the final map.
        if (Build.VERSION.SDK_INT >= 34 && gainmap != null) target.setGainmap(null)
        val canvas = Canvas(target)
        if (opaqueBackground) canvas.drawColor(Color.WHITE, PorterDuff.Mode.DST_OVER)
        if (layout == null) {
            if (Build.VERSION.SDK_INT >= 34 && gainmap != null) target.setGainmap(gainmap)
            return
        }
        val box = layout.box.let { RectF(it.left, it.top, it.right, it.bottom) }
        val path = Path().apply { addRoundRect(box, layout.radius, layout.radius, Path.Direction.CW) }
        val save = canvas.save()
        try {
            canvas.clipPath(path)
            if (layout.blurRadius > 0f) paintBackdrop(target, canvas, layout)
            canvas.drawColor(Color.argb((s.opacity * 255).roundToInt(), 90, 90, 90))
        } finally { canvas.restoreToCount(save) }
        // Text color remains opaque; the same runs also draw the HDR mask.
        CardTextRenderer(typography, layout.fontSize).drawLines(canvas, layout)
        if (Build.VERSION.SDK_INT >= 34 && gainmap != null) HdrGainmaps.attachOverlay(target, gainmap, layout, typography, s.opacity > 0f || s.blur > 0f)
    }

    private fun paintBackdrop(source: Bitmap, canvas: Canvas, layout: CardLayout) {
        val halo = ceil(layout.blurRadius * 3).toInt() + 2
        val region = Rect(
            (floor(layout.box.left).toInt() - halo).coerceAtLeast(0),
            (floor(layout.box.top).toInt() - halo).coerceAtLeast(0),
            (ceil(layout.box.right).toInt() + halo).coerceAtMost(source.width),
            (ceil(layout.box.bottom).toInt() + halo).coerceAtMost(source.height),
        )
        // Blur only a padded card region, at roughly the reference resolution, not the full photo.
        val ratio = minOf(1f, 512f / maxOf(region.width(), region.height()))
        val width = maxOf(1, (region.width() * ratio).roundToInt())
        val height = maxOf(1, (region.height() * ratio).roundToInt())
        val patch = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val sampling = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            Canvas(patch).drawBitmap(source, region, Rect(0, 0, width, height), sampling)
            val pixels = IntArray(width * height)
            patch.getPixels(pixels, 0, width, 0, 0, width, height)
            val radius = (layout.blurRadius * ratio).roundToInt().coerceAtLeast(1)
            val blurred = BoxBlur.blur(pixels, width, height, radius)
            patch.setPixels(blurred, 0, width, 0, 0, width, height)
            canvas.drawBitmap(patch, null, RectF(region), sampling)
        } finally { patch.recycle() }
    }
}
