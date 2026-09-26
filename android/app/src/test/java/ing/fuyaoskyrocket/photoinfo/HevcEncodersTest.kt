package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.platform.HevcEncoderKind
import ing.fuyaoskyrocket.photoinfo.platform.HevcEncoders
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The encoder selection only ever falls back: a missing x265 build or a platform
 * preference both resolve to the platform encoder, never the other way round.
 */
class HevcEncodersTest {
    @Test fun unavailableX265DegradesToThePlatformEncoder() {
        HevcEncoders.preferred = HevcEncoderKind.X265
        // On the JVM no native library exists, mirroring a 32-bit APK split.
        assertEquals(false, HevcEncoders.x265Available)
        assertEquals(HevcEncoderKind.PLATFORM, HevcEncoders.active())
    }

    @Test fun platformPreferenceStaysPlatform() {
        HevcEncoders.preferred = HevcEncoderKind.PLATFORM
        assertEquals(HevcEncoderKind.PLATFORM, HevcEncoders.active())
        HevcEncoders.preferred = HevcEncoderKind.X265
    }
}
