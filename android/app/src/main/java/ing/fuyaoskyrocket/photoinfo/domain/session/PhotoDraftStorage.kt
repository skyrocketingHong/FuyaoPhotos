package ing.fuyaoskyrocket.photoinfo.domain.session

import java.io.File
import java.util.UUID

enum class PhotoSessionKind(val directoryName: String) { CARDS("drafts"), METADATA("metadata-drafts"), COLORS("color-drafts") }

class PhotoDraftStorage(filesDirectory: File, kind: PhotoSessionKind) {
    private val directory = File(filesDirectory, kind.directoryName).apply { mkdirs() }

    fun newFile(): File = File(directory, "${UUID.randomUUID()}.photo")

    fun restoreFile(path: String): File {
        val file = File(path).canonicalFile
        require(file.parentFile == directory.canonicalFile && file.isFile) { "Draft is unavailable" }
        return file
    }

    fun removeOthers(keep: Collection<File>) {
        val retained = keep.map(File::getCanonicalFile).toSet()
        directory.listFiles()?.filter { it.isFile && it.extension == "photo" && it.canonicalFile !in retained }
            ?.forEach { it.delete() }
    }
}
