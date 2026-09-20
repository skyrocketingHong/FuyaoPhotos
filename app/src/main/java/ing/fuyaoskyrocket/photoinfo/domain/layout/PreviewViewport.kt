package ing.fuyaoskyrocket.photoinfo.domain.layout

import kotlin.math.max
import kotlin.math.min

data class PreviewOffset(val x:Float,val y:Float)

/** Keeps direct gestures and animated reset inside the same aspect-fit image bounds. */
object PreviewViewport {
    fun clamp(imageWidth:Int,imageHeight:Int,viewportWidth:Int,viewportHeight:Int,scale:Float,x:Float,y:Float):PreviewOffset {
        require(imageWidth>0 && imageHeight>0)
        if(viewportWidth<=0 || viewportHeight<=0 || !scale.isFinite() || scale<=0)return PreviewOffset(0f,0f)
        val fit=min(viewportWidth.toFloat()/imageWidth,viewportHeight.toFloat()/imageHeight)
        val limitX=max(0f,(imageWidth*fit*scale-viewportWidth)/2f)
        val limitY=max(0f,(imageHeight*fit*scale-viewportHeight)/2f)
        return PreviewOffset(x.coerceIn(-limitX,limitX),y.coerceIn(-limitY,limitY))
    }
}
