package ing.fuyaoskyrocket.photoinfo.domain.layout

object PageGeometry {
    const val MARGIN = 20f
    const val CARD_INSET = 20f

    fun scrollEdgeProgress(scrollDp: Float): Float = ((scrollDp - MARGIN) / 24f).coerceIn(0f, 1f)
}
