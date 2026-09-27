package ing.fuyaoskyrocket.photoinfo.features.colors.ui.photo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Resolves a finger position to a sampling point without applying a visual offset.
 *
 * A finger inside the rendered image samples its exact position. A finger just outside the image
 * remains valid within [hitSlop] and is clamped to the nearest image edge, so the first and last
 * rows and columns stay reachable even when the image touches a physical screen edge.
 */
internal fun samplingPointAtFinger(
    finger: Offset,
    layout: ImageLayout,
    hitSlop: Float,
): Offset? {
    val bounds = layout.imageBounds
    if (bounds.containsInclusive(finger)) return finger
    if (hitSlop < 0f) return null

    val nearestImagePoint = bounds.clamp(finger)
    return nearestImagePoint.takeIf {
        (it - finger).getDistanceSquared() <= hitSlop * hitSlop
    }
}

private fun Rect.containsInclusive(point: Offset): Boolean =
    point.x in left..right && point.y in top..bottom

private fun Rect.clamp(point: Offset): Offset = Offset(
    x = point.x.coerceIn(left, right),
    y = point.y.coerceIn(top, bottom),
)
