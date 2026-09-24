package ing.fuyaoskyrocket.photoinfo.domain.media

import kotlin.math.ln
import kotlin.math.pow

object GainmapMath {
    /** Encoded gain required to leave a linear base sample unchanged at full HDR headroom. */
    fun neutral(base:Double,min:Double,max:Double,gamma:Double,epsilonSdr:Double,epsilonHdr:Double):Double {
        require(listOf(base,min,max,gamma,epsilonSdr,epsilonHdr).all { it.isFinite() } && min>0 && max>=min && gamma>0)
        if(max==min)return 0.0
        val ratio=(base+epsilonHdr).coerceAtLeast(1e-12)/(base+epsilonSdr).coerceAtLeast(1e-12)
        return ((ln(ratio)-ln(min))/(ln(max)-ln(min))).coerceIn(0.0,1.0).pow(1.0/gamma)
    }
}
