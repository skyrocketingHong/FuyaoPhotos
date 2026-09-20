package ing.fuyaoskyrocket.photoinfo.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import ing.fuyaoskyrocket.photoinfo.domain.metadata.MetadataFormatting as Format
import ing.fuyaoskyrocket.photoinfo.domain.model.FieldId
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoInfo
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Only selected photographs are copied to private storage. No gallery-wide permission or network. */
class PhotoRepository(context: Context) {
    private val resolver = context.contentResolver
    private val directory = File(context.filesDir, "drafts").apply { mkdirs() }

    suspend fun import(uri: Uri): PhotoSource {
        val file = File(directory, "${UUID.randomUUID()}.photo")
        try {
            resolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > 512L * 1024 * 1024) throw IOException("Photo exceeds 512 MB")
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException("Cannot open selected photo")
            return inspect(file)
        } catch (failure: Throwable) {
            file.delete()
            throw failure
        }
    }

    fun restore(path: String): PhotoSource {
        val file = File(path).canonicalFile
        require(file.parentFile == directory.canonicalFile && file.isFile) { "Draft is unavailable" }
        return inspect(file)
    }

    fun removeOtherDrafts(keep: File) {
        directory.listFiles()?.filter { it != keep }?.forEach { it.delete() }
    }

    private fun inspect(file: File): PhotoSource {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported or corrupt photo" }
        require(bounds.outWidth.toLong() * bounds.outHeight <= 200_000_000L) { "Photo exceeds 200 MP" }
        val exif = try { ExifInterface(file) } catch (_: IOException) { null }
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) ?: 1
        val swapped = orientation in 5..8
        val width = if (swapped) bounds.outHeight else bounds.outWidth
        val height = if (swapped) bounds.outWidth else bounds.outHeight
        fun text(tag: String) = exif?.getAttribute(tag).orEmpty().trim()
        fun number(tag: String) = exif?.getAttributeDouble(tag, 0.0) ?: 0.0
        val info = PhotoInfo(mapOf(
            FieldId.DEVICE to Format.device(text(ExifInterface.TAG_MAKE), text(ExifInterface.TAG_MODEL)),
            FieldId.AUTHOR to text(ExifInterface.TAG_ARTIST),
            // GPS does not contain a place name. Never invent one or send coordinates to a geocoder.
            FieldId.LOCATION to "",
            FieldId.CAMERA to text(ExifInterface.TAG_LENS_MODEL),
            FieldId.IMAGE_SIZE to Format.megapixels(width, height),
            FieldId.FOCAL_LENGTH to Format.equivalentFocalLength(number(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)),
            FieldId.EXPOSURE to Format.exposure(number(ExifInterface.TAG_EXPOSURE_TIME)),
            FieldId.APERTURE to Format.number(number(ExifInterface.TAG_F_NUMBER)),
            FieldId.ISO to exif?.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
                ?.takeIf { it > 0 }?.toString().orEmpty(),
        ))
        val tags = CAPTURE_TAGS.mapNotNull { tag -> text(tag).takeIf { it.isNotEmpty() }?.let { tag to it } }.toMap()
        return PhotoSource(file, width, height, orientation, info, tags)
    }

    /** Decode an SDR, sRGB bitmap. EXIF orientation, including mirroring, is applied exactly once. */
    fun decode(source: PhotoSource, preview: Boolean): Bitmap {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            inMutable = true
            inSampleSize = 1
            if (preview) {
                while (maxOf(source.width, source.height) / inSampleSize > 2048) inSampleSize *= 2
            }
        }
        val decoded = BitmapFactory.decodeFile(source.file.absolutePath, options)
            ?: throw IOException("This Android version cannot decode this photo")
        if (Build.VERSION.SDK_INT >= 34 && decoded.hasGainmap()) decoded.setGainmap(null)
        if (source.orientation !in 2..8) return decoded
        val matrix = Matrix().apply {
            when (source.orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(90f); postScale(1f, -1f) }
                8 -> setRotate(270f)
            }
        }
        val oriented = try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } catch (error: Throwable) { decoded.recycle(); throw error }
        if (oriented !== decoded) decoded.recycle()
        if (oriented.isMutable) return oriented
        return try {
            oriented.copy(Bitmap.Config.ARGB_8888, true) ?: throw IOException("Cannot allocate photo")
        } finally { oriented.recycle() }
    }

    companion object {
        // Explicit allowlist excludes GPS, serial numbers, MakerNote, XMP and embedded thumbnails.
        private val CAPTURE_TAGS = listOf(
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_FLASH,
        )
    }
}
