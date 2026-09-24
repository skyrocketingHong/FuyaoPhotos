package ing.fuyaoskyrocket.photoinfo.domain.layout

/** Measured content dimensions already exclude the app bar and visible keyboard. */
object EditorWorkspacePolicy {
    data class Layout(val sideBySide: Boolean, val inspectorWidth: Float, val previewHeight: Float)

    fun calculate(width: Float, height: Float, fontScale: Float, imeVisible: Boolean): Layout {
        val inspector = if (fontScale > 1.3f) 420f else 360f
        val sideBySide = false
        val preview = when {
            imeVisible && height < 280f -> 0f
            imeVisible -> minOf(width * .75f, height * .28f)
            else -> minOf(width * .75f + 52f, height * .55f, (height - 220f).coerceAtLeast(52f))
        }
        return Layout(sideBySide, inspector, preview)
    }
}
