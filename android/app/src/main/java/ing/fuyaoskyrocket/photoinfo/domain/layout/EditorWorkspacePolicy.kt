package ing.fuyaoskyrocket.photoinfo.domain.layout

/** Measured content dimensions already exclude the app bar and visible keyboard. */
object EditorWorkspacePolicy {
    const val MIN_PHOTO_WIDTH = 280f
    const val PANE_GAP = 12f

    data class Layout(val sideBySide: Boolean, val inspectorWidth: Float, val previewHeight: Float)

    fun calculate(width: Float, height: Float, fontScale: Float, imeVisible: Boolean): Layout {
        val inspector = if (fontScale > 1.3f) 420f else 360f
        val sideBySide = width > height && width >= MIN_PHOTO_WIDTH + inspector + PANE_GAP
        val minimumPreview = (height - 80f).coerceIn(72f, 120f)
        val preview = when {
            sideBySide -> height
            imeVisible -> minOf(
                maxOf(minimumPreview, minOf(width * .4f, height * .28f)),
                (height - 96f).coerceAtLeast(72f),
            )
            else -> maxOf(minimumPreview, minOf(
                width * .75f + 52f,
                height * .55f,
                (height - 220f).coerceAtLeast(96f),
            ))
        }
        return Layout(sideBySide, inspector, preview.coerceIn(0f, height.coerceAtLeast(0f)))
    }

    fun canSplitAcrossFold(photoWidth: Float, controlsWidth: Float, fontScale: Float): Boolean =
        photoWidth >= MIN_PHOTO_WIDTH && controlsWidth >= (if (fontScale > 1.3f) 420f else 360f)
}
