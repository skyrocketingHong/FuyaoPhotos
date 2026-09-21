package ing.fuyaoskyrocket.photoinfo.platform

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import ing.fuyaoskyrocket.photoinfo.R
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.UUID

class FontRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("font", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "fonts").apply { mkdirs() }
    private val roundedTypeface = runCatching {
        Typeface.Builder(context.assets, "fonts/SF-Compact-Rounded.ttf")
            .setFontVariationSettings("'wght' 500").setWeight(500).build()
    }.getOrNull()
    private val monoTypeface = runCatching {
        Typeface.createFromAsset(context.assets, "fonts/SF-Mono-Medium.otf")
    }.getOrNull()
    internal val defaultTypography = CardTypography(
        letters = roundedTypeface ?: Typeface.create("sans-serif-medium", Typeface.NORMAL),
        numbers = monoTypeface ?: if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.MONOSPACE, 500, false) else Typeface.MONOSPACE,
        // cv04 centers colons vertically; cv05 keeps the legible capital I.
        // pnum preserves the narrow base-font 1 rather than a tabular alternate.
        letterFeatures = if (roundedTypeface != null) "'cv04' 1, 'cv05' 1, 'pnum' 1" else "'pnum' 1",
        mixedDigits = true,
    )
    var typography: CardTypography = load(); private set
    val displayName: String get() = preferences.getString("name", null)
        ?: context.getString(if (roundedTypeface != null && monoTypeface != null) R.string.reference_fonts else R.string.system_mixed_fonts)
    val selectionKey: String get() = preferences.getString("file", null)
        ?: "reference-v5-optical-one:${roundedTypeface != null}:${monoTypeface != null}"
    val hasCustomFont: Boolean get() = preferences.contains("file")

    private fun load(): CardTypography {
        val file = preferences.getString("file", null)?.let { File(directory, it) }
        return if (file?.isFile == true) runCatching { CardTypography.uniform(requireNotNull(Typeface.Builder(file).build())) }
            .getOrDefault(defaultTypography) else defaultTypography
    }

    fun import(uri: Uri) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "Custom font"
        // ASVS 5.3.2: the display name never becomes a filesystem path.
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
                        // ASVS 5.2.1: bound input before handing it to the native font parser.
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
            typography = CardTypography.uniform(loaded)
            directory.listFiles()?.filter { it != temporary }?.forEach { it.delete() }
        } catch (failure: Throwable) { temporary.delete(); throw failure }
    }

    fun reset() {
        preferences.edit().clear().apply()
        typography = defaultTypography
        directory.listFiles()?.forEach { it.delete() }
    }
}
