package ing.fuyaoskyrocket.photoinfo.domain.media

import java.util.Locale

internal object AppleRenderingMetadata {
    private val keys = setOf(
        "content.identifier", "still-image-time", "video-orientation", "live-photo-info",
        "live-photo-still-image-transform", "live-photo-still-image-transform-reference-dimensions",
        "live-photo.auto", "full-frame-rate-playback-intent", "live-photo.vitality-score",
        "live-photo.vitality-scoring-version", "live-photo.subject-relighting-applied-curve-parameter",
        "smartstyle-info", "smartstyle.rendering-version", "smartstyle.tone", "smartstyle.color",
        "smartstyle.intensity", "smartstyle.bypassed", "smartstyle.cast",
        "texturestyle-info", "texturestyle.rendering-version", "texturestyle.preset",
        "texturestyle.intensity", "texturestyle.grain",
    ).mapTo(mutableSetOf()) { "com.apple.quicktime.$it" }

    fun keepsKey(key: String): Boolean = key.lowercase(Locale.ROOT).removePrefix("mdta/") in keys

    fun keepsAuxiliaryTag(value: String): Boolean = value.removePrefix("com.apple.quicktime.video-map.") in
        setOf("sky", "smart-style-linear-thumbnail", "smart-style-delta-map", "person", "skin") && value.startsWith("com.apple.quicktime.video-map.")
}
