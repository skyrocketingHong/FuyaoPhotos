package ing.fuyaoskyrocket.photoinfo.platform

/** Which engine encodes HEVC stills; x265 is the default, the platform encoder the fallback. */
enum class HevcEncoderKind { X265, PLATFORM }

/**
 * Process-wide encoder preference, applied from the persisted setting by the view model.
 * An unavailable x265 build always degrades to the platform encoder.
 */
object HevcEncoders {
    @Volatile
    var preferred: HevcEncoderKind = HevcEncoderKind.X265

    val x265Available: Boolean get() = HevcX265.available

    fun active(): HevcEncoderKind =
        if (preferred == HevcEncoderKind.X265 && x265Available) HevcEncoderKind.X265 else HevcEncoderKind.PLATFORM
}
