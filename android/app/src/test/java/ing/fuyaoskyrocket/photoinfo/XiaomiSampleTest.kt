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

        val tail = XiaomiPortraitTail.read(file, part)
        val layout = XiaomiPortraitTail.layout(tail)
        val decoded = XiaomiPortraitDepth.decode(tail)
        assertTrue(decoded.sourceWidth > 0 && decoded.sourceHeight > 0)
        assertTrue(decoded.disparity.width > 0 && decoded.disparity.height > 0)

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
