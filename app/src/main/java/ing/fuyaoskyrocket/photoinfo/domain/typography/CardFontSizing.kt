package ing.fuyaoskyrocket.photoinfo.domain.typography

/** Equal em sizes do not imply equal visible capitals across font families. */
object CardFontSizing {
    fun matchCapHeight(baseHeight: Float, referenceHeight: Float): Float =
        if (baseHeight.isFinite() && referenceHeight.isFinite() && baseHeight > 0f && referenceHeight > 0f)
            referenceHeight / baseHeight
        else 1f
}
