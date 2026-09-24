package ing.fuyaoskyrocket.photoinfo.platform

import java.io.File

/** A bounded MP4/MOV range in the app-owned original photo; never an external path. */
data class MotionClipSource(val file: File, val offset: Long, val length: Long) {
    fun validate() {
        // ASVS 2.2.1: validate the range before handing the descriptor to the native decoder.
        require(file.isFile && offset >= 0 && length > 0 && length <= file.length() && offset <= file.length() - length)
    }
}
