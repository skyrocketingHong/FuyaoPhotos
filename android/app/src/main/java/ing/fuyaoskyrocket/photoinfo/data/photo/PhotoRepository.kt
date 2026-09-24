package ing.fuyaoskyrocket.photoinfo.data.photo

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
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
import ing.fuyaoskyrocket.photoinfo.domain.metadata.AndroidLensMetadata
import ing.fuyaoskyrocket.photoinfo.domain.metadata.PhotoCoordinates
import ing.fuyaoskyrocket.photoinfo.domain.media.HeifGraph
import ing.fuyaoskyrocket.photoinfo.domain.media.MediaEnvelope
import ing.fuyaoskyrocket.photoinfo.domain.media.MotionPhoto
import ing.fuyaoskyrocket.photoinfo.data.settings.SettingsRepository
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Only selected photographs are copied; ACCESS_MEDIA_LOCATION never reads the device's current location. */
class PhotoRepository(private val context: Context) {
    private val resolver = context.contentResolver
    private val directory = File(context.filesDir, "drafts").apply { mkdirs() }

    suspend fun import(uri: Uri): PhotoSource {
        val file = File(directory, "${UUID.randomUUID()}.photo")
        try {
            openPhoto(uri)?.use { input ->
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

    private fun openPhoto(uri: Uri): java.io.InputStream? {
        if (Build.VERSION.SDK_INT >= 29 && uri.authority == MediaStore.AUTHORITY &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val original = runCatching { resolver.openInputStream(MediaStore.setRequireOriginal(uri)) }.getOrNull()
            if (original != null) return original
        }
        // Providers may still redact GPS. File import and manual location entry remain available.
        return resolver.openInputStream(uri)
    }

    fun restore(path: String): PhotoSource {
        val file = File(path).canonicalFile
        require(file.parentFile == directory.canonicalFile && file.isFile) { "Draft is unavailable" }
        return inspect(file)
    }

    fun removeOtherDrafts(keep: File) {
        removeOtherDrafts(listOf(keep))
    }

    fun removeOtherDrafts(keep: Collection<File>) {
        val retained = keep.toSet()
        directory.listFiles()?.filter { it.isFile && it.extension == "photo" && it !in retained }?.forEach { it.delete() }
    }

    internal fun inspect(file: File): PhotoSource {
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
        val settings = SettingsRepository(context).read()
        val lens = AndroidLensMetadata.resolve(text(ExifInterface.TAG_MAKE), text(ExifInterface.TAG_MODEL),
            text(ExifInterface.TAG_LENS_MODEL), number(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM),
            settings.mainFocalMm, settings.lenses, number(ExifInterface.TAG_FOCAL_LENGTH))
        val coordinates = runCatching { exif?.latLong }.getOrNull()?.let { PhotoCoordinates.from(it[0], it[1]) }
        val info = PhotoInfo(mapOf(
            FieldId.DEVICE to lens.deviceName.ifBlank { Format.device(text(ExifInterface.TAG_MAKE), text(ExifInterface.TAG_MODEL)) },
            FieldId.AUTHOR to text(ExifInterface.TAG_ARTIST),
            // Place names are resolved asynchronously so a slow geocoder never blocks importing.
            FieldId.LOCATION to "",
            FieldId.CAMERA to lens.camera,
            FieldId.IMAGE_SIZE to Format.megapixels(width, height),
            FieldId.FOCAL_LENGTH to lens.focalLength,
            FieldId.EXPOSURE to Format.exposure(number(ExifInterface.TAG_EXPOSURE_TIME)),
            FieldId.APERTURE to Format.number(number(ExifInterface.TAG_F_NUMBER)),
            FieldId.ISO to exif?.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
                ?.takeIf { it > 0 }?.toString().orEmpty(),
        ))
        val tags = ing.fuyaoskyrocket.photoinfo.domain.metadata.ExportMetadata.readableTags.mapNotNull { tag -> text(tag).takeIf { it.isNotEmpty() }?.let { tag to it } }.toMap()
        val mime = bounds.outMimeType.orEmpty()
        val media = if (mime in setOf("image/heic", "image/heif", "image/avif")) {
            val graph = runCatching { HeifGraph.inspect(file) }.getOrNull()
            val motion = graph?.motionPayload?.let { payload ->
                runCatching { MotionPhoto.inspectHeif(file, exif?.getAttribute(ExifInterface.TAG_XMP), payload) }.getOrNull()
            }
            val still = runCatching {
                ing.fuyaoskyrocket.photoinfo.domain.media.HeifImageContainer.read(file).also { it.validateEditable() }
            }.getOrNull()
            val accepted = still != null || (graph != null && !graph.hasUnsupportedItems && !graph.hasIsoGainMap &&
                graph.toneMapItemId == null && !graph.hasStyleMetadata && !graph.hasPortraitMetadata &&
                (graph.motionPayload == null || motion != null))
            MediaEnvelope(jpeg = false, hdrHint = still?.gainMap() != null || still?.hdrTransfer==true, motion = motion,
                blocked = !accepted,bitDepth=still?.bitDepth ?: 8,hdrTransfer=still?.hdrTransfer==true,
                hdrTransferCode=still?.hdrTransferCode ?: 0)
        } else MotionPhoto.inspect(file, mime, exif?.getAttribute(ExifInterface.TAG_XMP))
        return PhotoSource(file, width, height, orientation, info, tags, coordinates, media)
    }

    /** Keep the decoded color space and gainmap. EXIF orientation applies to both base and gainmap. */
    fun decode(source: PhotoSource, preview: Boolean): Bitmap {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = if(source.media.bitDepth>8)Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
            if(Build.VERSION.SDK_INT>=34 && source.media.hdrTransfer) {
                inPreferredColorSpace=ColorSpace.get(if(source.media.hdrTransferCode==18)ColorSpace.Named.BT2020_HLG else ColorSpace.Named.BT2020_PQ)
            }
            inMutable = true
            inSampleSize = 1
            if (preview) {
                while (maxOf(source.width, source.height) / inSampleSize > 2048) inSampleSize *= 2
            }
        }
        val decoded = BitmapFactory.decodeFile(source.file.absolutePath, options)
            ?: throw IOException("This Android version cannot decode this photo")
        if (Build.VERSION.SDK_INT >= 34 && !source.media.jpeg && source.media.hdrHint && !source.media.hdrTransfer && !decoded.hasGainmap()) {
            try { ing.fuyaoskyrocket.photoinfo.platform.HeifGainmaps.attach(source.file, decoded, options.inSampleSize) }
            catch (failure: Throwable) { decoded.recycle(); throw failure }
        }
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
            val copy = oriented.copy(if(source.media.bitDepth>8)Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888, true) ?: throw IOException("Cannot allocate photo")
            if (Build.VERSION.SDK_INT >= 34 && oriented.hasGainmap()) copy.setGainmap(oriented.gainmap)
            copy
        } finally { oriented.recycle() }
    }

}
