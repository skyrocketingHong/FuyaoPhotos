package ing.fuyaoskyrocket.photoinfo.data.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import ing.fuyaoskyrocket.photoinfo.data.photo.PhotoRepository
import ing.fuyaoskyrocket.photoinfo.data.photo.PhotoSource
import ing.fuyaoskyrocket.photoinfo.domain.model.CardStyle
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportFormat
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoInfo
import ing.fuyaoskyrocket.photoinfo.platform.CardRenderer
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class PhotoExporter(private val context: Context, private val photos: PhotoRepository) {
    private val renderer = CardRenderer()

    suspend fun export(
        source: PhotoSource, info: PhotoInfo, style: CardStyle, typeface: Typeface,
        format: ExportFormat, keepCaptureMetadata: Boolean, destination: Uri? = null,
    ): Uri {
        // Never silently reduce export resolution to avoid an allocation failure.
        val runtime = Runtime.getRuntime()
        val freeHeap = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        val imageBytes = source.width.toLong() * source.height * 4
        val peakBytes = imageBytes * (if (source.orientation in 2..8) 2 else 1) + 24L * 1024 * 1024
        val temporary = File.createTempFile("export-", ".${format.extension}", context.cacheDir)
        try {
            if (peakBytes > freeHeap * .85) throw OutOfMemoryError("Insufficient memory for original-resolution export")
            val bitmap = photos.decode(source, preview = false)
            try {
                currentCoroutineContext().ensureActive()
                renderer.drawInPlace(bitmap, info, style, typeface)
                if (format == ExportFormat.JPEG) {
                    Canvas(bitmap).drawColor(Color.WHITE, PorterDuff.Mode.DST_OVER)
                }
                temporary.outputStream().use { output ->
                    val codec = if (format == ExportFormat.JPEG) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                    if (!bitmap.compress(codec, 97, output)) throw IOException("Image encoding failed")
                }
            } finally { bitmap.recycle() }
            if (keepCaptureMetadata) {
                val exif = ExifInterface(temporary)
                source.captureTags.forEach { (key, value) -> exif.setAttribute(key, value) }
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, "1")
                exif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, source.width.toString())
                exif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, source.height.toString())
                exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Fuyao Photo Info 1.0.0")
                exif.saveAttributes()
            }
            currentCoroutineContext().ensureActive()
            return publish(temporary, format, destination)
        } catch (failure: Throwable) {
            if (destination != null) runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            throw failure
        } finally { temporary.delete() }
    }

    private fun publish(file: File, format: ExportFormat, destination: Uri?): Uri {
        val resolver = context.contentResolver
        val usingMediaStore = destination == null
        val uri = destination ?: run {
            check(Build.VERSION.SDK_INT >= 29) { "Use the system Save As dialog on Android 8/9" }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename(format))
                put(MediaStore.Images.Media.MIME_TYPE, format.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FuyaoPhotoInfo")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Cannot create gallery item")
        }
        try {
            resolver.openOutputStream(uri, "w")?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: throw IOException("Cannot write exported photo")
            if (usingMediaStore && Build.VERSION.SDK_INT >= 29) {
                val updated = resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
                check(updated > 0) { "Cannot publish gallery item" }
            }
            return uri
        } catch (failure: Throwable) {
            runCatching {
                if (usingMediaStore) resolver.delete(uri, null, null)
                else DocumentsContract.deleteDocument(resolver, uri)
            }
            throw failure
        }
    }

    companion object {
        fun filename(format: ExportFormat) = "Fuyao_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date())}.${format.extension}"
    }
}
