package ing.fuyaoskyrocket.photoinfo.domain.typography

/** Equal em sizes do not imply equal visible capitals across font families. */
object CardFontSizing {
    // A narrow 1 needs a small optical boost after geometric cap-height matching.
    const val PROPORTIONAL_ONE_OPTICAL_SCALE = 1.03f

    fun matchCapHeight(baseHeight: Float, referenceHeight: Float): Float =
        if (baseHeight.isFinite() && referenceHeight.isFinite() && baseHeight > 0f && referenceHeight > 0f)
            referenceHeight / baseHeight
        else 1f
}
