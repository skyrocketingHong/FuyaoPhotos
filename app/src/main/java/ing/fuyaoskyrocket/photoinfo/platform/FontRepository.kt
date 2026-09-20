package ing.fuyaoskyrocket.photoinfo.platform

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.UUID

class FontRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("font", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "fonts").apply { mkdirs() }
    var typeface: Typeface = load(); private set
    val displayName: String? get() = preferences.getString("name", null)

    private fun load(): Typeface {
        val file = preferences.getString("file", null)?.let { File(directory, it) }
        return if (file?.isFile == true) runCatching { requireNotNull(Typeface.Builder(file).build()) }
            .getOrDefault(Typeface.MONOSPACE) else Typeface.MONOSPACE
    }

    fun import(uri: Uri) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "Custom font"
        val temporary = File(directory, "${UUID.randomUUID()}.font")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var size = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > 10 * 1024 * 1024) throw IOException("Font exceeds 10 MB")
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException("Cannot open font")
            val signature = java.io.DataInputStream(temporary.inputStream()).use { input ->
                ByteArray(4).also { input.readFully(it) }
            }
            val sfnt = signature.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                String(signature, Charsets.US_ASCII) in setOf("OTTO", "ttcf", "true")
            require(sfnt) { "Select a valid TTF, OTF or TTC font" }
            val loaded = requireNotNull(Typeface.Builder(temporary).build()) { "Invalid font" }
            preferences.edit().putString("file", temporary.name).putString("name", name).apply()
            typeface = loaded
            directory.listFiles()?.filter { it != temporary }?.forEach { it.delete() }
        } catch (failure: Throwable) { temporary.delete(); throw failure }
    }

    fun reset() {
        preferences.edit().clear().apply()
        typeface = Typeface.MONOSPACE
        directory.listFiles()?.forEach { it.delete() }
    }
}
