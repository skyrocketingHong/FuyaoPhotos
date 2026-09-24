package ing.fuyaoskyrocket.photoinfo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Gainmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import ing.fuyaoskyrocket.photoinfo.data.export.HeicEncoder
import ing.fuyaoskyrocket.photoinfo.domain.media.HeifImageContainer
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportFormat
import ing.fuyaoskyrocket.photoinfo.platform.HeifGainmaps
import ing.fuyaoskyrocket.photoinfo.platform.ImageEncoderSupport
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion=34)
class ModernImageExportTest {
    @Test fun heicKeepsP3BaseAndAlphaGainMap() = gainMapRoundTrip(ExportFormat.HEIC)
    @Test fun avifKeepsP3BaseAndAlphaGainMap() = gainMapRoundTrip(ExportFormat.AVIF)

    private fun gainMapRoundTrip(format: ExportFormat) {
        assumeTrue(ImageEncoderSupport.supports(format))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val base=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888,false,ColorSpace.get(ColorSpace.Named.DISPLAY_P3))
        base.eraseColor(Color.GRAY)
        val pixels=Bitmap.createBitmap(64,64,Bitmap.Config.ALPHA_8)
        val bytes=java.nio.ByteBuffer.allocate(pixels.byteCount)
        bytes.put(ByteArray(pixels.byteCount) { 192.toByte() }); bytes.rewind(); pixels.copyPixelsFromBuffer(bytes)
        base.setGainmap(Gainmap(pixels).apply { setRatioMax(4f,4f,4f);setGamma(2f,2f,2f);setDisplayRatioForFullHdr(4f) })
        val output=File.createTempFile("gain-roundtrip-",".${format.extension}",context.cacheDir)
        try {
            HeicEncoder.encode(base,output,100,null,emptyMap(),format==ExportFormat.AVIF)
            val result=BitmapFactory.decodeFile(output.absolutePath)!!
            try {
                HeifGainmaps.attach(output,result,1)
                assertEquals(base.colorSpace?.name,result.colorSpace?.name)
                assertEquals(2f,result.gainmap!!.gamma[0],.001f)
                assertEquals(4f,result.gainmap!!.displayRatioForFullHdr,.001f)
                assertTrue(Color.red(result.gainmap!!.gainmapContents.getPixel(20,20)) in 184..200)
            } finally { result.recycle() }
        } finally { output.delete();base.recycle();pixels.recycle() }
    }

    @Test fun avifPreservesTenBitHdrTransfer() {
        assumeTrue(ImageEncoderSupport.supports(ExportFormat.AVIF))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap=Bitmap.createBitmap(128,128,Bitmap.Config.RGBA_F16,false,ColorSpace.get(ColorSpace.Named.BT2020_PQ))
        bitmap.eraseColor(Color.GRAY)
        val output=File.createTempFile("ten-bit-",".avif",context.cacheDir)
        try {
            HeicEncoder.encode(bitmap,output,100,null,emptyMap(),true)
            val image=HeifImageContainer.read(output)
            assertEquals(10,image.bitDepth);assertEquals(16,image.hdrTransferCode)
            val result=BitmapFactory.decodeFile(output.absolutePath,BitmapFactory.Options().apply {
                inPreferredConfig=Bitmap.Config.RGBA_F16;inPreferredColorSpace=bitmap.colorSpace
            })!!
            try { assertEquals(Bitmap.Config.RGBA_F16,result.config);assertEquals(bitmap.colorSpace?.name,result.colorSpace?.name) }
            finally { result.recycle() }
        } finally { output.delete();bitmap.recycle() }
    }
}
