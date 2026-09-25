package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.JpegContainer
import ing.fuyaoskyrocket.photoinfo.domain.media.MotionPhoto
import ing.fuyaoskyrocket.photoinfo.domain.media.XiaomiPortraitDepth
import ing.fuyaoskyrocket.photoinfo.domain.media.XiaomiPortraitTail
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportOptions
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs the real-device Xiaomi samples kept in the git-ignored docs/samples directory.
 * These tests skip everywhere except machines that have the files, mirroring the
 * FUYAO_PORTRAIT_SAMPLE oracle pattern.
 */
class XiaomiSampleTest {
    private fun sample(name: String): File? {
        var directory = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            val candidate = File(directory, "docs/samples/$name")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return null
        }
        return null
    }

    @Test fun plainHdrLivePhotoImportsWithGainMapAndVideo() {
        val file = sample("xiaomi-hdr-live-plain-20260924-122908.jpg")
        assumeTrue("sample not present", file != null)
        val envelope = MotionPhoto.inspect(file!!, "image/jpeg")
        assertFalse("reason=${envelope.blockReason}", envelope.blocked)
        assertTrue(envelope.hdrHint)
        val motion = requireNotNull(envelope.motion)
        assertEquals(file.length(), motion.offset + motion.length)
        assertTrue(motion.length > 1_000_000)
        assertNull(envelope.portraitTail)
        assertEquals(1, JpegContainer.auxiliary(JpegContainer.inspect(file)).size)
    }

    @Test fun portraitHdrLivePhotoCarriesRecognizedDepthTail() {
        val file = sample("xiaomi-hdr-live-portrait-20260924-094528.jpg")
        assumeTrue("sample not present", file != null)
        val envelope = MotionPhoto.inspect(file!!, "image/jpeg")
        assertFalse("reason=${envelope.blockReason}", envelope.blocked)
        assertTrue(envelope.hdrHint)
        val motion = requireNotNull(envelope.motion)
        assertEquals(file.length(), motion.offset + motion.length)
        val part = requireNotNull(envelope.portraitTail)
        assertTrue(part.offset > 0 && part.length > 1_024)

        // XMP declares depthOrientation=90 over an upright-stored capture, so the plane
        // aligns with the photo only after the inverse 270-degree turn (EXIF orientation 8).
        assertEquals(90, envelope.portraitDepthDegrees)
        val tail = XiaomiPortraitTail.read(file, part)
        val layout = XiaomiPortraitTail.layout(tail)
        val decoded = XiaomiPortraitDepth.decode(tail, envelope.portraitDepthDegrees, 0)
        assertEquals(8, decoded.orientation)
        assertEquals(4096, decoded.sourceWidth)
        assertEquals(3072, decoded.sourceHeight)
        assertEquals(1024, decoded.disparity.width)
        assertEquals(768, decoded.disparity.height)
        // Focus sits on the subject: after the Apple-style inversion, the subject's
        // disparity is clearly above the borders.
        val ranks = decoded.disparity.pixels
        fun mean(from: Int, until: Int): Int {
            var total = 0; var count = 0
            for (at in from until until step 7) { total += ranks[at].toInt() and 255; count++ }
            return total / count
        }
        val plane = decoded.disparity.width * decoded.disparity.height
        val center = mean(plane / 2 - decoded.disparity.width * 8, plane / 2 + decoded.disparity.width * 8)
        val border = (mean(0, decoded.disparity.width * 8) +
            mean(plane - decoded.disparity.width * 8, plane)) / 2
        assertTrue("center disparity $center should exceed border $border", center > border + 10)

        // The export merge and the filtered rewrite must keep the declaration intact.
        val merged = MotionPhoto.xiaomiPortraitXmp(JpegContainer.inspect(file), null)
        assertTrue(merged.contains("capsInfo"))
        val options = ExportOptions(keepExif = true, keepLocation = false, keepCaptureTime = false)
        val output = ByteArrayOutputStream()
        val filtered = XiaomiPortraitTail.writeFiltered(file, part, output, options)
        assertEquals(tail.size, filtered.size)
        assertEquals(layout.secondEnd, XiaomiPortraitTail.layout(filtered).secondEnd)
        assertEquals(output.toByteArray().size, tail.size)
    }
}
