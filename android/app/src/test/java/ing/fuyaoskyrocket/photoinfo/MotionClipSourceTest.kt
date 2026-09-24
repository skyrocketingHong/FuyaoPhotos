package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.platform.MotionClipSource
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class MotionClipSourceTest {
    @Test fun onlyRangesFullyInsideThePrivatePhotoCanReachTheDecoder() {
        val file=File.createTempFile("motion-range", ".photo")
        try {
            file.writeBytes(ByteArray(100))
            MotionClipSource(file,20,80).validate()
            for((offset,length) in listOf(-1L to 20L,0L to 0L,100L to 1L,20L to 81L,Long.MAX_VALUE to 20L,1L to Long.MAX_VALUE)) {
                assertThrows(IllegalArgumentException::class.java) { MotionClipSource(file,offset,length).validate() }
            }
        } finally { file.delete() }
        assertThrows(IllegalArgumentException::class.java) { MotionClipSource(file,0,1).validate() }
    }
}
