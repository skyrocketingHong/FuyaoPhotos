package ing.fuyaoskyrocket.photoinfo.data.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ing.fuyaoskyrocket.photoinfo.platform.CardTypography
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import ing.fuyaoskyrocket.photoinfo.domain.metadata.ExportMetadata
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.data.photo.PhotoRepository
import ing.fuyaoskyrocket.photoinfo.data.photo.PhotoSource
import ing.fuyaoskyrocket.photoinfo.domain.model.*
import ing.fuyaoskyrocket.photoinfo.domain.media.*
import ing.fuyaoskyrocket.photoinfo.platform.CardRenderer
import ing.fuyaoskyrocket.photoinfo.platform.HdrGainmaps
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class PhotoExporter(private val context: Context, private val photos: PhotoRepository) {
    private val renderer=CardRenderer()
    suspend fun export(source:PhotoSource,info:PhotoInfo,style:CardStyle,typography:CardTypography,
        format:ExportFormat,keepCaptureMetadata:Boolean,destination:Uri?=null,jpegQuality:Int=DEFAULT_JPEG_QUALITY,
        options:ExportOptions=ExportOptions(format,jpegQuality,keepCaptureMetadata,keepCaptureTime=keepCaptureMetadata)):Uri {
        val media=source.media
        require(!media.blocked) { context.getString(R.string.media_unsupported) }
        require(!media.hdrHint || Build.VERSION.SDK_INT>=34) { context.getString(R.string.hdr_requires_android14) }
        require(format==ExportFormat.JPEG || (!media.hdrHint && media.motion==null)) { context.getString(R.string.preservation_requires_jpeg) }
        val runtime=Runtime.getRuntime();val freeHeap=runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory())
        val imageBytes=source.width.toLong()*source.height*4
        val peak=imageBytes*(if(source.orientation in 2..8)2 else 1)+32L*1024*1024
        val encoded=File.createTempFile("encoded-",".${format.extension}",context.cacheDir)
        val assembled=File.createTempFile("export-",".${format.extension}",context.cacheDir)
        val metadata=File.createTempFile("metadata-",".jpg",context.cacheDir)
        val videoFile=File.createTempFile("video-", ".mp4", context.cacheDir)
        val selectedTags=ExportMetadata.select(source.captureTags,options)
        try {
            if(media.motion!=null) {
                try { VideoMetadata.copy(source.file,media.motion.offset,media.motion.length,videoFile,options) }
                catch(failure:Exception) { throw IOException(context.getString(R.string.video_metadata_unsupported),failure) }
            }
            if(peak>freeHeap*.8)throw OutOfMemoryError()
            var expectedGain:FloatArray?=null
            var expectedColor:String?=null
            val bitmap=photos.decode(source,preview=false)
            try {
                currentCoroutineContext().ensureActive()
                val hdr=Build.VERSION.SDK_INT>=34 && bitmap.hasGainmap()
                require(!media.hdrHint || hdr) { context.getString(R.string.hdr_not_decoded) }
                require(!hdr || format==ExportFormat.JPEG) { context.getString(R.string.preservation_requires_jpeg) }
                require(bitmap.colorSpace?.name?.let { !it.contains("HLG",true) && !it.contains("PQ",true) } != false) { context.getString(R.string.media_unsupported) }
                if(Build.VERSION.SDK_INT>=36 && hdr)require(requireNotNull(bitmap.gainmap) { context.getString(R.string.hdr_not_decoded) }.gainmapDirection==android.graphics.Gainmap.GAINMAP_DIRECTION_SDR_TO_HDR) { context.getString(R.string.media_unsupported) }
                renderer.drawInPlace(bitmap,info,style,typography,opaqueBackground=format==ExportFormat.JPEG)
                if(Build.VERSION.SDK_INT>=34 && hdr)expectedGain=HdrGainmaps.metadata(requireNotNull(bitmap.gainmap) { context.getString(R.string.hdr_not_preserved) })
                expectedColor=bitmap.colorSpace?.name
                encoded.outputStream().use { output ->
                    val codec=if(format==ExportFormat.JPEG)Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                    if(!bitmap.compress(codec,jpegQuality.coerceIn(0,100),output))throw IOException("Image encoding failed")
                }
            } finally { bitmap.recycle() }
            if(format==ExportFormat.JPEG) {
                val exif=if(selectedTags.isNotEmpty()) {
                    val tiny=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
                    try { metadata.outputStream().use { check(tiny.compress(Bitmap.CompressFormat.JPEG,90,it)) } } finally { tiny.recycle() }
                    writeCaptureExif(metadata,source,selectedTags)
                    JpegContainer.exif(JpegContainer.inspect(metadata))
                } else emptyList()
                val layout=JpegContainer.inspect(encoded)
                JpegContainer.rewrite(encoded,assembled,exif,MotionPhoto.xmp(layout,media.motion))
                // Assemble into private storage, then validate before publishing any output.
                if(media.motion!=null) {
                    java.io.FileOutputStream(assembled,true).use { JpegContainer.copyRange(videoFile,0,media.motion.length,it) }
                }
                val verified=MotionPhoto.inspect(assembled,"image/jpeg")
                require(!verified.blocked) { context.getString(R.string.media_validation_failed) }
                media.motion?.let { original ->
                    val video=requireNotNull(verified.motion)
                    require(video.length==original.length && video.timestampUs==original.timestampUs &&
                        MotionPhoto.digest(videoFile,0,original.length).contentEquals(MotionPhoto.digest(assembled,video.offset,video.length))) { context.getString(R.string.media_validation_failed) }
                }
                val options=BitmapFactory.Options().apply {
                    var sample=1
                    while(maxOf(source.width,source.height)/sample>1024)sample*=2
                    inSampleSize=sample
                }
                val checkBitmap=BitmapFactory.decodeFile(assembled.absolutePath,options) ?: throw IOException(context.getString(R.string.media_validation_failed))
                try {
                    if(Build.VERSION.SDK_INT>=34 && expectedGain!=null) {
                        require(checkBitmap.hasGainmap() && HdrGainmaps.matches(expectedGain,HdrGainmaps.metadata(checkBitmap.gainmap!!))) { context.getString(R.string.hdr_not_preserved) }
                    }
                    require(checkBitmap.colorSpace?.name==expectedColor) { context.getString(R.string.media_validation_failed) }
                } finally { checkBitmap.recycle() }
            } else {
                encoded.copyTo(assembled,overwrite=true)
                if(selectedTags.isNotEmpty())writeCaptureExif(assembled,source,selectedTags)
            }
            currentCoroutineContext().ensureActive()
            return publish(assembled,format,destination,media.motion!=null,
                if(options.keepCaptureTime) ExportMetadata.capturedAt(selectedTags) else null)
        } catch(failure:Throwable) {
            if(destination!=null)runCatching { DocumentsContract.deleteDocument(context.contentResolver,destination) }
            throw failure
        } finally { encoded.delete();assembled.delete();metadata.delete();videoFile.delete() }
    }
    private fun writeCaptureExif(file:File,source:PhotoSource,tags:Map<String,String>) {
        ExifInterface(file).apply {
            tags.forEach { (tag,value)->setAttribute(tag,value) }
            setAttribute(ExifInterface.TAG_ORIENTATION,"1")
            setAttribute(ExifInterface.TAG_IMAGE_WIDTH,source.width.toString());setAttribute(ExifInterface.TAG_IMAGE_LENGTH,source.height.toString())
            setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION,source.width.toString());setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION,source.height.toString())
            setAttribute(ExifInterface.TAG_SOFTWARE,"Fuyao Photo Info ${ing.fuyaoskyrocket.photoinfo.BuildConfig.MARKETING_VERSION} (${ing.fuyaoskyrocket.photoinfo.BuildConfig.BUILD_NUMBER})")
            saveAttributes()
        }
    }
    private fun publish(file:File,format:ExportFormat,destination:Uri?,motion:Boolean,capturedAt:Long?):Uri {
        val resolver=context.contentResolver;val gallery=destination==null
        val uri=destination ?: run {
            check(Build.VERSION.SDK_INT>=29)
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,filename(format,motion));put(MediaStore.Images.Media.MIME_TYPE,format.mime)
                if(capturedAt!=null)put(MediaStore.Images.Media.DATE_TAKEN,capturedAt)
                put(MediaStore.Images.Media.RELATIVE_PATH,"${Environment.DIRECTORY_PICTURES}/FuyaoPhotoInfo");put(MediaStore.Images.Media.IS_PENDING,1)
            }) ?: throw IOException("Cannot create gallery item")
        }
        try {
            resolver.openOutputStream(uri,"w")?.use { output->file.inputStream().use { it.copyTo(output) } } ?: throw IOException("Cannot write exported photo")
            if(gallery && Build.VERSION.SDK_INT>=29)check(resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)>0)
            return uri
        } catch(failure:Throwable) {
            runCatching { if(gallery)resolver.delete(uri,null,null) else DocumentsContract.deleteDocument(resolver,uri) };throw failure
        }
    }
    companion object {
        const val DEFAULT_JPEG_QUALITY = 100
        fun filename(format:ExportFormat,motion:Boolean=false)="Fuyao_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.ROOT).format(Date())}${if(motion) "_MP" else ""}.${format.extension}"
    }
}
