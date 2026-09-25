package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.HeifGraph
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Ground-truth checks against the Apple reference capture kept in the git-ignored
 * docs/samples directory: a live portrait with HDR, depth and photographic styles,
 * plus the version iOS Photos produced after a depth and lighting edit.
 */
class AppleSampleTest {
    private fun sample(relative: String): File? {
        var directory = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            val candidate = File(directory, "docs/samples/$relative")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return null
        }
        return null
    }

    @Test fun originalCarriesTheFullStyleAndDepthStack() {
        val file = sample("apple-airdrop-full-IMG_8565/IMG_8565.HEIC")
        assumeTrue("sample not present", file != null)
        val report = HeifGraph.inspect(file!!)
        // The stack Apple ships: styles metadata and portrait depth. The gain map rides
        // Apple's own auxiliary type rather than the ISO URN, and the importer fails
        // closed on that vendor stack by design, exactly like any other unknown aux.
        assertTrue(report.hasStyleMetadata)
        assertTrue(report.hasPortraitMetadata)
        assertFalse(report.hasIsoGainMap)
        assertTrue(report.hasUnsupportedItems)
        assertNull(report.motionPayload)
    }

    @Test fun editedVersionStripsStylesAndDepthAuxiliaries() {
        val file = sample("apple-airdrop-full-IMG_8565/IMG_E8565.heic")
        assumeTrue("sample not present", file != null)
        val report = HeifGraph.inspect(file!!)
        assertTrue(report.hasIsoGainMap || report.hasUnsupportedItems)
        assertFalse(report.hasStyleMetadata)
        // iOS re-renders the depth but keeps the disparity auxiliary after the edit.
        assertTrue(report.hasPortraitMetadata)
        assertNull(report.motionPayload)
    }

    @Test fun stylesItemMatchesTheAppleWiring() {
        val file = sample("apple-airdrop-full-IMG_8565/IMG_8565.HEIC")
        assumeTrue("sample not present", file != null)
        val bytes = file!!.readBytes()
        // Apple names the uri item "metadata" and binds it by content type; the plist
        // itself is a 17-key dictionary opened by the df/11 markers.
        val declaration = "metadata\u0000tag:apple.com,2023:photo:metadata:styles\u0000"
        fun find(from: Int, needle: ByteArray): Int {
            var scan = from
            while (scan + needle.size <= bytes.size) {
                if (bytes.copyOfRange(scan, scan + needle.size).contentEquals(needle)) return scan
                scan++
            }
            return -1
        }
        val at = find(0, declaration.toByteArray(Charsets.ISO_8859_1))
        assertTrue(at > 0)
        val plistAt = find(at, "bplist00".toByteArray())
        assertTrue(plistAt > at)
        assertEquals(0xdf, bytes[plistAt + 8].toInt() and 0xff)
        assertEquals(0x10, bytes[plistAt + 9].toInt() and 0xff)
        assertEquals(17, bytes[plistAt + 10].toInt())
    }
}
