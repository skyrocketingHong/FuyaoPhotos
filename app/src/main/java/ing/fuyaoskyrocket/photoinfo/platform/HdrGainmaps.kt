package ing.fuyaoskyrocket.photoinfo.platform

import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import ing.fuyaoskyrocket.photoinfo.domain.layout.CardLayout
import ing.fuyaoskyrocket.photoinfo.domain.media.GainmapMath
import kotlin.math.roundToInt

@RequiresApi(34)
object HdrGainmaps {
    fun metadata(g:Gainmap):FloatArray {
        val values=(g.ratioMin.toList()+g.ratioMax.toList()+g.gamma.toList()+g.epsilonSdr.toList()+g.epsilonHdr.toList()+listOf(g.minDisplayRatioForHdrTransition,g.displayRatioForFullHdr)).toMutableList()
        if(Build.VERSION.SDK_INT>=36) {
            values+=g.gainmapDirection.toFloat()
            val primaries=g.alternativeImagePrimaries as? ColorSpace.Rgb
            values+=if(primaries==null)0f else 1f
            if(primaries!=null) { values+=primaries.primaries.toList();values+=primaries.whitePoint.toList() }
        }
        return values.toFloatArray()
    }
    fun matches(a:FloatArray,b:FloatArray):Boolean = a.size==b.size && a.indices.all { kotlin.math.abs(a[it]-b[it])<=.001f*maxOf(1f,kotlin.math.abs(a[it])) }

    fun copyMetadata(source:Gainmap,contents:Bitmap):Gainmap {
        if(Build.VERSION.SDK_INT>=35)return Gainmap(source,contents)
        return Gainmap(contents).apply {
            source.ratioMin.let { setRatioMin(it[0],it[1],it[2]) }
            source.ratioMax.let { setRatioMax(it[0],it[1],it[2]) }
            source.gamma.let { setGamma(it[0],it[1],it[2]) }
            source.epsilonSdr.let { setEpsilonSdr(it[0],it[1],it[2]) }
            source.epsilonHdr.let { setEpsilonHdr(it[0],it[1],it[2]) }
            displayRatioForFullHdr=source.displayRatioForFullHdr
            minDisplayRatioForHdrTransition=source.minDisplayRatioForHdrTransition
        }
    }
    fun attachOverlay(target:Bitmap,source:Gainmap,layout:CardLayout,typography:CardTypography,backgroundChanged:Boolean) {
        val old=source.gainmapContents
        val runtime=Runtime.getRuntime()
        val available=runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory())
        if(old.width.toLong()*old.height*16 > available*.8)throw OutOfMemoryError("Gainmap editing exceeds available memory")
        val contents=checkNotNull(old.copy(Bitmap.Config.ARGB_8888,true))
        val pixels=IntArray(old.width*old.height)
        old.getPixels(pixels,0,old.width,0,0,old.width,old.height)
        if(old.config==Bitmap.Config.ALPHA_8)for(i in pixels.indices) {
            val value=Color.alpha(pixels[i]);pixels[i]=Color.rgb(value,value,value)
        }
        val mask=Bitmap.createBitmap(old.width,old.height,Bitmap.Config.ARGB_8888)
        try {
            val sx=old.width.toFloat()/target.width;val sy=old.height.toFloat()/target.height
            Canvas(mask).apply {
                scale(sx,sy)
                val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { color=Color.WHITE }
                if(backgroundChanged) {
                    drawRoundRect(RectF(layout.box.left,layout.box.top,layout.box.right,layout.box.bottom),layout.radius,layout.radius,paint)
                } else {
                    CardTextRenderer(typography,layout.fontSize).drawLines(this,layout,mask=true)
                }
            }
            val coverage=IntArray(pixels.size);mask.getPixels(coverage,0,old.width,0,0,old.width,old.height)
            val min=source.ratioMin;val max=source.ratioMax;val gamma=source.gamma;val sdr=source.epsilonSdr;val hdr=source.epsilonHdr
            val rgb=target.colorSpace as? ColorSpace.Rgb
            for(y in 0 until old.height)for(x in 0 until old.width) {
                val i=y*old.width+x;val alpha=Color.alpha(coverage[i])/255.0
                if(alpha==0.0)continue
                val color=target.getColor(((x+.5f)/sx).toInt().coerceIn(0,target.width-1),((y+.5f)/sy).toInt().coerceIn(0,target.height-1))
                val values=doubleArrayOf(color.red().toDouble(),color.green().toDouble(),color.blue().toDouble())
                val original=intArrayOf(Color.red(pixels[i]),Color.green(pixels[i]),Color.blue(pixels[i]))
                val channels=IntArray(3) { c ->
                    val linear=rgb?.eotf?.applyAsDouble(values[c]) ?: values[c]
                    val neutral=GainmapMath.neutral(linear,min[c].toDouble(),max[c].toDouble(),gamma[c].toDouble(),sdr[c].toDouble(),hdr[c].toDouble())*255
                    (original[c]*(1-alpha)+neutral*alpha).roundToInt().coerceIn(0,255)
                }
                pixels[i]=Color.rgb(channels[0],channels[1],channels[2])
            }
            contents.setPixels(pixels,0,old.width,0,0,old.width,old.height)
            target.setGainmap(copyMetadata(source,contents))
        } catch(failure:Throwable) { contents.recycle();throw failure }
        finally { mask.recycle() }
    }
}
