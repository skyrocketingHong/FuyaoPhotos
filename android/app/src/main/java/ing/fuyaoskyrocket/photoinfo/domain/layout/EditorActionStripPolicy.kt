package ing.fuyaoskyrocket.photoinfo.domain.layout

object EditorActionStripPolicy {
    const val ACTION_WIDTH = 48f
    const val SAVE_WIDTH = 56f
    const val SPACING = 4f

    data class Layout(
        val inlineToolCount: Int,
        val hasOverflow: Boolean,
        val scrolls: Boolean,
        val contentWidth: Float,
    )

    fun calculate(availableWidth: Float, toolCount: Int): Layout {
        require(toolCount >= 0)
        for (inlineCount in toolCount downTo 0) {
            val overflow = inlineCount < toolCount
            val width = contentWidth(inlineCount, overflow)
            if (width <= availableWidth) return Layout(inlineCount, overflow, false, width)
        }
        return Layout(0, toolCount > 0, true, contentWidth(0, toolCount > 0))
    }

    private fun contentWidth(inlineCount: Int, overflow: Boolean): Float {
        val otherActions = inlineCount + if (overflow) 1 else 0
        return ACTION_WIDTH + SAVE_WIDTH + otherActions * ACTION_WIDTH + (otherActions + 1) * SPACING
    }
}
