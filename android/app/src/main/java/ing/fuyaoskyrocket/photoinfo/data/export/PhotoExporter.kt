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
    data class Result(val image: Uri, val movie: Uri? = null)
    private val renderer=CardRenderer()
    suspend fun export(source:PhotoSource,info:PhotoInfo,style:CardStyle,typography:CardTypography,
        format:ExportFormat,keepCaptureMetadata:Boolean,destination:Uri?=null,jpegQuality:Int=DEFAULT_JPEG_QUALITY,
        options:ExportOptions=ExportOptions(format,jpegQuality,keepCaptureMetadata,keepCaptureTime=keepCaptureMetadata),
        pairDirectory:Uri?=null):Result {
        val media=source.media
        require(ing.fuyaoskyrocket.photoinfo.platform.ImageEncoderSupport.supports(format)) {
            context.getString(R.string.image_encoder_unavailable)
        }
        require(media.bitDepth<=8 || format==ExportFormat.AVIF) { context.getString(R.string.avif_precision_required) }
        val paired = options.separateLivePhoto && media.motion != null
        val convertPortrait = options.applePortrait && media.portraitTail != null
        require(!convertPortrait || format==ExportFormat.HEIC) { context.getString(R.string.portrait_heic_required) }
        // HDR, live pairing, portrait conversion and style injection are independent;
        // each only constrains the format (HDR-capable, pairing-capable, HEIC).
        val injectStyle = options.appleStyle && format == ExportFormat.HEIC
        require(!options.appleStyle || format==ExportFormat.HEIC) { context.getString(R.string.style_heic_required) }
        val styleIdentifier = if (injectStyle) AppleStyleMetadata.newIdentifier() else null
        // Apple tags portrait output with CustomRendered=9 alongside the depth auxiliary images.
        val selectedTags=ExportMetadata.select(source.captureTags,options) +
            if(convertPortrait) mapOf("CustomRendered" to "9") else emptyMap()
        require(!paired || (pairDirectory!=null && format in setOf(ExportFormat.JPEG,ExportFormat.HEIC))) {
            context.getString(R.string.live_pair_format)
        }
        require(!media.blocked) { context.getString(R.string.media_unsupported) }
        require(!media.hdrHint || Build.VERSION.SDK_INT>=34) { context.getString(R.string.hdr_requires_android14) }
        require(format==ExportFormat.JPEG || ((media.motion==null || paired) && (media.portraitTail==null || convertPortrait) &&
            (!media.hdrHint || format!=ExportFormat.PNG))) {
            context.getString(R.string.preservation_requires_jpeg)
        }
        require(format!=ExportFormat.HEIC || Build.VERSION.SDK_INT>=28) { context.getString(R.string.heic_requires_android9) }
        require(format!=ExportFormat.AVIF || Build.VERSION.SDK_INT>=34) { context.getString(R.string.avif_requires_android14) }
        val runtime=Runtime.getRuntime();val freeHeap=runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory())
        val imageBytes=source.width.toLong()*source.height*(if(media.bitDepth>8)8 else 4)
        val peak=imageBytes*(if(source.orientation in 2..8)2 else 1)+32L*1024*1024
        val encoded=File.createTempFile("encoded-",".${format.extension}",context.cacheDir)
        val assembled=File.createTempFile("export-",".${format.extension}",context.cacheDir)
        val metadata=File.createTempFile("metadata-",".jpg",context.cacheDir)
        val videoFile=File.createTempFile("video-", ".mp4", context.cacheDir)
        val pairedMovie=File.createTempFile("paired-", ".mov", context.cacheDir)
        val unblurred=File.createTempFile("unblurred-", ".jpg", context.cacheDir)
        val identifier=if(paired)java.util.UUID.randomUUID().toString().uppercase(Locale.ROOT) else null
        var stage=R.string.export_stage_prepare
        try {
            var renderSource=source
            val portrait=if(convertPortrait) {
                stage=R.string.export_stage_depth
                val part=requireNotNull(media.portraitTail)
                val tail=XiaomiPortraitTail.read(source.file,part)
                val decoded=XiaomiPortraitDepth.decode(tail)
                val length=XiaomiPortraitTail.layout(tail).secondEnd
                unblurred.outputStream().use { it.write(tail,0,length) }
                val raw=photos.inspect(unblurred)
                require(!raw.media.blocked && raw.width==source.width && raw.height==source.height) {
                    context.getString(R.string.media_validation_failed)
                }
                val width=if(decoded.orientation in 5..8)raw.height else raw.width
                val height=if(decoded.orientation in 5..8)raw.width else raw.height
                require(width==decoded.sourceWidth && height==decoded.sourceHeight) { context.getString(R.string.media_validation_failed) }
                renderSource=raw
                decoded
            } else null
            if(media.motion!=null) {
                stage=R.string.export_stage_video
                try { VideoMetadata.copy(source.file,media.motion.offset,media.motion.length,videoFile,options) }
                catch(failure:Exception) { throw IOException(context.getString(R.string.video_metadata_unsupported),failure) }
            }
            if(peak>freeHeap*.8)throw OutOfMemoryError()
            val captureExif=if(selectedTags.isNotEmpty()) {
                val tiny=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
                try { metadata.outputStream().use { check(tiny.compress(Bitmap.CompressFormat.JPEG,90,it)) } } finally { tiny.recycle() }
                writeCaptureExif(metadata,source,selectedTags)
                JpegContainer.exif(JpegContainer.inspect(metadata))
            } else emptyList()
            val selectedExif=when {
                identifier!=null || convertPortrait || styleIdentifier!=null -> listOf(
                    ApplePhotoMetadata.withAppleNotes(captureExif.singleOrNull(),identifier,convertPortrait,styleIdentifier))
                else -> captureExif
            }
            if(identifier!=null)AppleLivePhotoMovie.write(videoFile,pairedMovie,identifier,requireNotNull(media.motion).timestampUs)
            var expectedGain:FloatArray?=null
            var expectedColor:String?=null
            var portraitBytes:ByteArray?=null
            var styleAssets:StyleAssets?=null
            stage=R.string.export_stage_decode
            val bitmap=photos.decode(renderSource,preview=false)
            try {
                currentCoroutineContext().ensureActive()
                val hdr=Build.VERSION.SDK_INT>=34 && bitmap.hasGainmap()
                require(!media.hdrHint || hdr || (media.hdrTransfer && bitmap.config==Bitmap.Config.RGBA_F16)) { context.getString(R.string.hdr_not_decoded) }
                require(!hdr || format!=ExportFormat.PNG) { context.getString(R.string.preservation_requires_jpeg) }
                require(media.hdrTransfer || bitmap.colorSpace?.name?.let { !it.contains("HLG",true) && !it.contains("PQ",true) } != false) { context.getString(R.string.media_unsupported) }
                if(Build.VERSION.SDK_INT>=36 && hdr)require(requireNotNull(bitmap.gainmap) { context.getString(R.string.hdr_not_decoded) }.gainmapDirection==android.graphics.Gainmap.GAINMAP_DIRECTION_SDR_TO_HDR) { context.getString(R.string.media_unsupported) }
                if(Build.VERSION.SDK_INT>=36 && hdr && format in setOf(ExportFormat.HEIC,ExportFormat.AVIF)) {
                    ing.fuyaoskyrocket.photoinfo.platform.AndroidGainMapMetadata.normalizeBasePrimaries(requireNotNull(bitmap.gainmap),bitmap.colorSpace)
                }
                stage=R.string.export_stage_encode
                renderer.drawInPlace(bitmap,info,style,typography,opaqueBackground=format!=ExportFormat.PNG)
                if(injectStyle)styleAssets=encodeStyleAssets(bitmap)
                if(Build.VERSION.SDK_INT>=34 && hdr)expectedGain=HdrGainmaps.metadata(requireNotNull(bitmap.gainmap) { context.getString(R.string.hdr_not_preserved) })
                expectedColor=bitmap.colorSpace?.name
                if(format==ExportFormat.HEIC || format==ExportFormat.AVIF) {
                    HeicEncoder.encode(bitmap,encoded,jpegQuality,selectedExif.singleOrNull(),selectedTags,format==ExportFormat.AVIF)
                } else encoded.outputStream().use { output ->
                    val codec=if(format==ExportFormat.JPEG)Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                    if(!bitmap.compress(codec,jpegQuality.coerceIn(0,100),output))throw IOException("Image encoding failed")
                }
            } finally { bitmap.recycle() }
            stage=R.string.export_stage_verify
            if(format==ExportFormat.JPEG) {
                val layout=JpegContainer.inspect(encoded)
                val xmp=MotionPhoto.xmp(layout,media.motion.takeUnless { paired }).let { packet ->
                    if(media.portraitTail!=null) MotionPhoto.xiaomiPortraitXmp(JpegContainer.inspect(source.file),packet)
                    else packet
                }
                JpegContainer.rewrite(encoded,assembled,selectedExif,xmp)
                // Assemble into private storage, then validate before publishing any output.
                // The original layout is primary, gain map, portrait tail, then the motion video.
                media.portraitTail?.let { part ->
                    portraitBytes=java.io.FileOutputStream(assembled,true).use {
                        XiaomiPortraitTail.writeFiltered(source.file,part,it,options)
                    }
                }
                if(media.motion!=null && !paired) {
                    java.io.FileOutputStream(assembled,true).use { JpegContainer.copyRange(videoFile,0,media.motion.length,it) }
                }
                val verified=MotionPhoto.inspect(assembled,"image/jpeg")
                require(!verified.blocked) { context.getString(R.string.media_validation_failed) }
                media.motion?.takeUnless { paired }?.let { original ->
                    val video=requireNotNull(verified.motion)
                    require(video.length==original.length && video.timestampUs==original.timestampUs &&
                        MotionPhoto.digest(videoFile,0,original.length).contentEquals(MotionPhoto.digest(assembled,video.offset,video.length))) { context.getString(R.string.media_validation_failed) }
                }
                media.portraitTail?.let { original ->
                    val result=requireNotNull(verified.portraitTail) { context.getString(R.string.media_validation_failed) }
                    val bytes=requireNotNull(portraitBytes)
                    require(result.length==original.length &&
                        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                            .contentEquals(MotionPhoto.digest(assembled,result.offset,result.length))) {
                        context.getString(R.string.media_validation_failed)
                    }
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
                if(format==ExportFormat.PNG && selectedTags.isNotEmpty())writeCaptureExif(assembled,source,selectedTags)
                if(format==ExportFormat.HEIC || format==ExportFormat.AVIF) {
                    val checked=HeifImageContainer.read(assembled)
                    checked.validateEditable()
                    require(checked.bitDepth>=media.bitDepth && checked.hdrTransfer==media.hdrTransfer) { context.getString(R.string.media_validation_failed) }
                    require((checked.gainMap()!=null)==(expectedGain!=null)) { context.getString(R.string.hdr_not_preserved) }
                    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    BitmapFactory.decodeFile(assembled.absolutePath,bounds)
                    require(bounds.outWidth==source.width && bounds.outHeight==source.height) { context.getString(R.string.media_validation_failed) }
                    val sample=BitmapFactory.Options().apply {
                        inPreferredConfig=if(media.bitDepth>8)Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
                        if(Build.VERSION.SDK_INT>=34 && media.hdrTransfer) inPreferredColorSpace=android.graphics.ColorSpace.get(
                            if(media.hdrTransferCode==18)android.graphics.ColorSpace.Named.BT2020_HLG else android.graphics.ColorSpace.Named.BT2020_PQ)
                        var factor=1
                        while(maxOf(source.width,source.height)/factor>1024)factor*=2
                        inSampleSize=factor
                    }
                    val decoded=BitmapFactory.decodeFile(assembled.absolutePath,sample)
                        ?: throw IOException(context.getString(R.string.media_validation_failed))
                    try {
                        require(decoded.colorSpace?.name==expectedColor) { context.getString(R.string.media_validation_failed) }
                        if(Build.VERSION.SDK_INT>=34 && expectedGain!=null) {
                            ing.fuyaoskyrocket.photoinfo.platform.HeifGainmaps.attach(assembled,decoded,sample.inSampleSize)
                            require(HdrGainmaps.matches(expectedGain,HdrGainmaps.metadata(requireNotNull(decoded.gainmap)))) {
                                context.getString(R.string.hdr_not_preserved)
                            }
                        }
                    }
                    finally { decoded.recycle() }
                }
            }
            if(portrait!=null) {
                stage=R.string.export_stage_depth_encode
                ApplePortraitEncoder.attach(assembled,portrait,portrait.orientation,captureAperture(source.captureTags))
            }
            if(styleAssets!=null) {
                stage=R.string.export_stage_style_encode
                val assets=requireNotNull(styleAssets)
                HeifImageContainer.read(assembled).withPhotographicStyles(assets.deltaWidth,assets.deltaHeight,
                    assets.landscape,assets.linear,assets.sky).write(assembled)
            }
            currentCoroutineContext().ensureActive()
            stage=R.string.export_stage_publish
            if(paired)return publishPair(assembled,pairedMovie,format,requireNotNull(pairDirectory))
            return Result(publish(assembled,format,destination,media.motion!=null,
                if(options.keepCaptureTime) ExportMetadata.capturedAt(selectedTags) else null))
        } catch(failure:Throwable) {
            if(destination!=null)runCatching { DocumentsContract.deleteDocument(context.contentResolver,destination) }
            if(failure is Exception && failure !is kotlinx.coroutines.CancellationException && failure !is SecurityException) {
                android.util.Log.e("PhotoExport","Export stage=$stage type=${failure.javaClass.simpleName}",failure)
                throw ExportStageException(stage,failure)
            }
            throw failure
        } finally { encoded.delete();assembled.delete();metadata.delete();videoFile.delete();pairedMovie.delete();unblurred.delete() }
    }
    private fun publishPair(image:File,movie:File,format:ExportFormat,directory:Uri):Result {
        val resolver=context.contentResolver
        val parent=DocumentsContract.buildDocumentUriUsingTree(directory,DocumentsContract.getTreeDocumentId(directory))
        val stem=filename(format).substringBeforeLast('.')
        val created=mutableListOf<Uri>()
        try {
            fun write(file:File,mime:String,name:String):Uri {
                val uri=DocumentsContract.createDocument(resolver,parent,mime,name) ?: throw IOException("Cannot create Live Photo resource")
                created+=uri
                resolver.openOutputStream(uri,"w")?.use { output->file.inputStream().use { it.copyTo(output) } }
                    ?: throw IOException("Cannot save Live Photo resource")
                return uri
            }
            val photo=write(image,format.mime,"$stem.${format.extension}")
            val video=write(movie,"video/quicktime","$stem.mov")
            return Result(photo,video)
        } catch(failure:Throwable) {
            created.forEach { runCatching { DocumentsContract.deleteDocument(resolver,it) } }
            throw failure
        }
    }
    private fun writeCaptureExif(file:File,source:PhotoSource,tags:Map<String,String>) {
        ExifInterface(file).apply {
            tags.forEach { (tag,value)->setAttribute(tag,value) }
            setAttribute(ExifInterface.TAG_ORIENTATION,"1")
            setAttribute(ExifInterface.TAG_IMAGE_WIDTH,source.width.toString());setAttribute(ExifInterface.TAG_IMAGE_LENGTH,source.height.toString())
            setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION,source.width.toString());setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION,source.height.toString())
            setAttribute(ExifInterface.TAG_SOFTWARE,"Fuyao Photos ${ing.fuyaoskyrocket.photoinfo.BuildConfig.MARKETING_VERSION} (${ing.fuyaoskyrocket.photoinfo.BuildConfig.BUILD_NUMBER})")
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
                put(MediaStore.Images.Media.RELATIVE_PATH,"${Environment.DIRECTORY_PICTURES}/FuyaoPhotos");put(MediaStore.Images.Media.IS_PENDING,1)
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
        private fun captureAperture(tags: Map<String, String>): Double? {
            val raw = tags["FNumber"] ?: return null
            val parts = raw.split('/')
            val value = if (parts.size == 2) parts[0].toDoubleOrNull()?.div(parts[1].toDoubleOrNull() ?: return null)
            else raw.toDoubleOrNull()
            return value?.takeIf { it.isFinite() && it > 0 }
        }
    }

    private class StyleAssets(val deltaWidth:Int,val deltaHeight:Int,val landscape:Boolean,
        val linear:HeifImageContainer,val sky:HeifImageContainer)

    /** Encodes the linear thumbnail and sky placeholder the style layer refers to. */
    private fun encodeStyleAssets(bitmap:Bitmap):StyleAssets {
        val landscape=bitmap.width>=bitmap.height
        val maxWidth=if(landscape)2880 else 2560
        val maxHeight=if(landscape)2560 else 2880
        val scale=minOf(1.0,minOf(maxWidth.toDouble()/bitmap.width,maxHeight.toDouble()/bitmap.height))
        fun fitted(value:Int)=(Math.round(value*scale/2.0).toInt().coerceAtLeast(1)*2).coerceAtMost(if(value==bitmap.width)maxWidth else maxHeight)
        val linearFile=File.createTempFile("style-linear-",".heic",context.cacheDir)
        val skyFile=File.createTempFile("style-sky-",".heic",context.cacheDir)
        try {
            val linear=linearThumbnail(bitmap)
            val sky=Bitmap.createBitmap(((bitmap.width/2) and -2).coerceAtLeast(2),
                ((bitmap.height/2) and -2).coerceAtLeast(2),Bitmap.Config.ARGB_8888)
            try {
                HeicEncoder.encode(linear,linearFile,100,null,emptyMap())
                HeicEncoder.encode(sky,skyFile,100,null,emptyMap())
            } finally { if(linear!==bitmap)linear.recycle();sky.recycle() }
            return StyleAssets(fitted(bitmap.width),fitted(bitmap.height),landscape,
                HeifImageContainer.read(linearFile),HeifImageContainer.read(skyFile))
        } finally { linearFile.delete();skyFile.delete() }
    }

    private fun linearThumbnail(bitmap:Bitmap):Bitmap {
        val target=4f/3f
        val ratio=bitmap.width.toFloat()/bitmap.height
        val cropWidth=if(ratio>target)(bitmap.height*target).toInt() else bitmap.width
        val cropHeight=if(ratio>target)bitmap.height else (bitmap.width/target).toInt()
        val cropped=if(cropWidth==bitmap.width && cropHeight==bitmap.height)bitmap else run {
            val created=Bitmap.createBitmap(bitmap,(bitmap.width-cropWidth)/2,(bitmap.height-cropHeight)/2,cropWidth,cropHeight)
            if(created==bitmap)bitmap else created
        }
        val scaled=Bitmap.createScaledBitmap(cropped,1024,768,true)
        if(scaled!==cropped && cropped!==bitmap)cropped.recycle()
        return scaled
    }
}

internal class ExportStageException(val stage:Int,cause:Throwable):IOException(cause)
