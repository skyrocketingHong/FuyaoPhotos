package ing.fuyaoskyrocket.photoinfo.domain.media

import java.io.*
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject

object FuyaoPhotosPackage {
    const val EXTENSION = "fuyaophotos"
    const val MIME = "application/vnd.fuyaophotos"
    const val MAX_RESOURCE_BYTES = 512L * 1024 * 1024
    const val MAX_BYTES = MAX_RESOURCE_BYTES * 2 + 32788
    private val magic = "FUYAOPHOTOS".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 1, 13, 10, 26)
    data class Resource(val fileExtension: String, val bytes: Long, val sha256: String) {
        fun json() = JSONObject().put("fileExtension", fileExtension).put("bytes", bytes).put("sha256", sha256)
    }
    data class Contents(val photo: File, val movie: File, val identifier: String, val stillImageTimeUs: Long)

    fun write(photo: File, movie: File, identifier: String, stillImageTimeUs: Long, destination: File) {
        val suffix = photo.extension.lowercase().let { if (it == "jpeg") "jpg" else it }
        require(suffix in setOf("jpg", "heic") && identifier.length == 36 && UUID.fromString(identifier).toString().equals(identifier, true))
        require(stillImageTimeUs in 0..60_000_000)
        fun resource(file: File, suffix: String): Resource {
            require(file.length() in 1..MAX_RESOURCE_BYTES)
            return file.inputStream().use { Resource(suffix, file.length(), copy(it, null, file.length())) }
        }
        val image = resource(photo, suffix); val video = resource(movie, "mov")
        val json = JSONObject().put("format", "fuyaophotos.live-photo").put("version", 1)
            .put("assetIdentifier", identifier).put("stillImageTimeUs", stillImageTimeUs)
            .put("photo", image.json()).put("movie", video.json()).toString().toByteArray(Charsets.UTF_8)
        require(json.size in 1..32768)
        try {
            DataOutputStream(BufferedOutputStream(destination.outputStream())).use { output ->
                output.write(magic); output.writeInt(json.size); output.write(json)
                listOf(photo to image, movie to video).forEach { (file, record) -> file.inputStream().use { input ->
                    require(copy(input, output, record.bytes) == record.sha256 && input.read() == -1)
                } }
            }
        } catch (failure: Throwable) { destination.delete(); throw failure }
    }

    fun read(source: File, directory: File): Contents {
        require(source.length() in 21..MAX_BYTES)
        DataInputStream(BufferedInputStream(source.inputStream())).use { input ->
            val signature = ByteArray(magic.size); input.readFully(signature); require(signature.contentEquals(magic))
            val size = input.readInt(); require(size in 1..32768)
            val bytes = ByteArray(size); input.readFully(bytes)
            val header = PortableJson.objectFrom(bytes, 32768)
            require(PortableJson.string(header, "format") == "fuyaophotos.live-photo" && integer(header, "version") == 1L)
            val id = PortableJson.string(header, "assetIdentifier")
            require(id.length == 36 && UUID.fromString(id).toString().equals(id, true))
            val time = integer(header, "stillImageTimeUs"); require(time in 0..60_000_000)
            fun resource(key: String): Resource {
                val value = header.getJSONObject(key)
                val length = integer(value, "bytes"); val hash = PortableJson.string(value, "sha256")
                require(length in 1..MAX_RESOURCE_BYTES && hash.matches(Regex("[0-9a-f]{64}")))
                return Resource(PortableJson.string(value, "fileExtension"), length, hash)
            }
            val photo = resource("photo"); val movie = resource("movie")
            require(photo.fileExtension in setOf("jpg", "heic") && movie.fileExtension == "mov")
            require(source.length() == 20 + size + photo.bytes + movie.bytes)
            // ASVS 5.2.1 / 5.3.2: fixed file names, bounded lengths, no paths or compressed entries.
            val image = File(directory, "photo.${photo.fileExtension}"); val video = File(directory, "video.mov")
            require(!image.exists() && !video.exists())
            try {
                listOf(image to photo, video to movie).forEach { (file, record) ->
                    file.outputStream().use { require(copy(input, it, record.bytes) == record.sha256) }
                }
                return Contents(image, video, id, time)
            } catch (failure: Throwable) { image.delete(); video.delete(); throw failure }
        }
    }

    fun recognizes(file: File) = file.inputStream().use { input ->
        val bytes = ByteArray(magic.size); input.read(bytes) == bytes.size && bytes.contentEquals(magic)
    }

    private fun integer(value: JSONObject, key: String): Long {
        val number = value.get(key); require(number is Number)
        val result = number.toLong()
        require(number.toDouble().isFinite() && number.toDouble() == result.toDouble())
        return result
    }

    private fun copy(input: InputStream, output: OutputStream?, count: Long): String {
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
        var remaining = count
        while (remaining > 0) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
            val read = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            require(read > 0)
            output?.write(buffer, 0, read); digest.update(buffer, 0, read); remaining -= read
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
