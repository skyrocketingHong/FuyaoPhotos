package ing.fuyaoskyrocket.photoinfo

import android.graphics.*
import ing.fuyaoskyrocket.photoinfo.platform.CardTypography
import ing.fuyaoskyrocket.photoinfo.platform.FontRepository
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import ing.fuyaoskyrocket.photoinfo.data.photo.PhotoRepository
import ing.fuyaoskyrocket.photoinfo.data.export.PhotoExporter
import ing.fuyaoskyrocket.photoinfo.domain.media.*
import ing.fuyaoskyrocket.photoinfo.domain.model.*
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion=34)
class HdrMotionExportTest {
    @Test
    @SdkSuppress(minSdkVersion = 35)
    fun jpegPreparationAttachesGainmapAfterAllCanvasDrawing() {
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val contents = Bitmap.createBitmap(200, 150, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val gainmap = Gainmap(contents).apply { setRatioMax(4f, 4f, 4f); displayRatioForFullHdr = 4f }
        try {
            // Regression trigger: even constructing a Canvas clears an attached gainmap.
            bitmap.setGainmap(gainmap)
            Canvas(bitmap)
            assertFalse(bitmap.hasGainmap())
            bitmap.setGainmap(gainmap)
            ing.fuyaoskyrocket.photoinfo.platform.CardRenderer().drawInPlace(bitmap,
                PhotoInfo(mapOf(FieldId.ISO to "100")), CardStyle(), CardTypography.uniform(Typeface.MONOSPACE), opaqueBackground = true)
            assertNotNull(bitmap.gainmap)
            assertEquals(4f, bitmap.gainmap!!.ratioMax[0], .001f)
            assertEquals(Color.WHITE, bitmap.getPixel(0, 0))
            val output = java.io.ByteArrayOutputStream()
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 97, output))
            val encoded = output.toByteArray()
            val decoded = requireNotNull(BitmapFactory.decodeByteArray(encoded, 0, encoded.size))
            try { assertTrue(decoded.hasGainmap()) } finally { decoded.recycle() }
        } finally { bitmap.recycle(); contents.recycle() }
    }

    @Test fun hdrStillSurvivesExportWithAndWithoutCaptureMetadata() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File.createTempFile("hdr-still-", ".jpg", context.cacheDir)
        val output = File.createTempFile("hdr-result-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val contents = Bitmap.createBitmap(200, 150, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        bitmap.setGainmap(Gainmap(contents).apply { setRatioMax(4f, 4f, 4f); displayRatioForFullHdr = 4f })
        try {
            input.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 97, it)) }
            val photos = PhotoRepository(context)
            val source = photos.import(Uri.fromFile(input))
            try {
                for (keepMetadata in listOf(false, true)) {
                    PhotoExporter(context, photos).export(source, PhotoInfo(mapOf(FieldId.ISO to "100")),
                        CardStyle(), CardTypography.uniform(Typeface.MONOSPACE), ExportFormat.JPEG, keepMetadata, Uri.fromFile(output))
                    val decoded = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
                    try {
                        assertTrue(decoded.hasGainmap())
                        assertEquals(4f, decoded.gainmap!!.ratioMax[0], .001f)
                        assertNull(MotionPhoto.inspect(output, "image/jpeg").motion)
                    } finally { decoded.recycle() }
                }
            } finally { source.file.delete() }
        } finally { bitmap.recycle(); contents.recycle(); input.delete(); output.delete() }
    }

    @Test fun transparentTextOnlyCardKeepsUntouchedGainmapPixels() {
        val source=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val map=Bitmap.createBitmap(200,150,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        source.setGainmap(Gainmap(map).apply { setRatioMax(4f,4f,4f);displayRatioForFullHdr=4f })
        val result=ing.fuyaoskyrocket.photoinfo.platform.CardRenderer().preview(source,
            PhotoInfo(mapOf(FieldId.ISO to "100")),CardStyle(opacity=0f,blur=0f),FontRepository(InstrumentationRegistry.getInstrumentation().targetContext).defaultTypography)
        try {
            val output=requireNotNull(result.gainmap).gainmapContents
            assertEquals(Color.WHITE,output.getPixel(160,120))
            assertEquals(Color.WHITE,map.getPixel(160,120))
            val pixels=IntArray(200*150);output.getPixels(pixels,0,200,0,0,200,150)
            assertTrue(pixels.any { Color.red(it)<255 })
        } finally { result.recycle();source.recycle();map.recycle() }
    }

    @Test fun hdrAndMotionSurviveCoverEditingAndExifInsertion() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val encoded=File.createTempFile("hdr-",".jpg",context.cacheDir)
        val sourceFile=File.createTempFile("motion-",".jpg",context.cacheDir)
        val output=File.createTempFile("result-",".jpg",context.cacheDir)
        val bitmap=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val contents=Bitmap.createBitmap(200,150,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val gain=Gainmap(contents).apply { setRatioMax(4f,4f,4f);displayRatioForFullHdr=4f }
        bitmap.setGainmap(gain)
        try {
            encoded.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,97,it)) }
            fun box(type:String,payload:ByteArray)=ByteBuffer.allocate(payload.size+8).putInt(payload.size+8).put(type.toByteArray()).put(payload).array()
            // Structural synthetic video exercises container preservation, not codec playback.
            val video=box("ftyp","isom0000".toByteArray())+box("moov",byteArrayOf())+box("mdat",ByteArray(128) { it.toByte() })
            JpegContainer.rewrite(encoded,sourceFile,emptyList(),MotionPhoto.xmp(JpegContainer.inspect(encoded),MotionVideo(0,video.size.toLong(),1000)))
            sourceFile.appendBytes(video)
            val photos=PhotoRepository(context);val source=photos.import(Uri.fromFile(sourceFile))
            try {
                assertFalse(source.media.blocked);assertNotNull(source.media.motion)
                PhotoExporter(context,photos).export(source,PhotoInfo(mapOf(FieldId.ISO to "100")),CardStyle(),CardTypography.uniform(Typeface.MONOSPACE),ExportFormat.JPEG,true,Uri.fromFile(output))
                val decoded=requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
                try {
                    assertTrue(decoded.hasGainmap());assertEquals(4f,decoded.gainmap!!.ratioMax[0],.001f)
                    val map=decoded.gainmap!!.gainmapContents
                    assertTrue(Color.red(map.getPixel(10,10))>240)
                } finally { decoded.recycle() }
                val final=requireNotNull(MotionPhoto.inspect(output,"image/jpeg").motion)
                assertEquals(1000L,final.timestampUs)
                assertArrayEquals(java.security.MessageDigest.getInstance("SHA-256").digest(video),MotionPhoto.digest(output,final.offset,final.length))
            } finally { source.file.delete() }
        } finally { bitmap.recycle();contents.recycle();encoded.delete();sourceFile.delete();output.delete() }
    }
}
