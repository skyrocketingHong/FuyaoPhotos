package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.*
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportOptions
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class VideoMetadataTest {
    private fun int(value:Int)=ByteBuffer.allocate(4).putInt(value).array()
    private fun box(type:String,payload:ByteArray)=box(type.toByteArray(Charsets.ISO_8859_1),payload)
    private fun box(type:ByteArray,payload:ByteArray)=int(payload.size+8)+type+payload
    private fun text(value:String)=value.toByteArray()
    private val media=ByteArray(4096) { (it%251).toByte() }
    private val created=int(0x12345678)+int(0x23456701)
    private fun fixture(full:Boolean=false, handler:String="vide"):ByteArray {
        val keys=listOf("com.apple.quicktime.location.ISO6709","com.apple.quicktime.creationdate","com.android.model")
        val values=listOf("LOCATION_SECRET","TIME_SECRET","MODEL_SAMPLE")
        val keyBox=box("keys",int(0)+int(keys.size)+keys.fold(byteArrayOf()) { acc,k -> acc+int(k.length+8)+text("mdta")+text(k) })
        val items=box("ilst",values.mapIndexed { i,v -> box(int(i+1),box("data",ByteArray(8)+text(v))) }.fold(byteArrayOf()){a,b->a+b})
        val hdlr=box("hdlr",int(0)+int(0)+text(handler)+ByteArray(12)+text("track"))
        val date=box("mvhd",int(0)+created+ByteArray(80))
        val track=box("trak",box("mdia",box("mdhd",int(0)+created+ByteArray(12))+hdlr))
        return box("ftyp",text("isom")+int(0)+text("isom"))+box("mdat",media)+box("moov",date+track+
            box("meta",(if(full)int(0) else byteArrayOf())+keyBox+items))
    }
    @Test fun eachMetadataCategoryCanBeRemovedWithoutChangingEncodedMediaOrSize() {
        for(full in listOf(false,true))for(exif in listOf(false,true))for(gps in listOf(false,true))for(time in listOf(false,true)) {
            val source=File.createTempFile("motion-source", ".mp4");val output=File.createTempFile("motion-clean", ".mp4")
            try {
                val bytes=fixture(full);source.writeBytes(bytes)
                VideoMetadata.copy(source,0,source.length(),output,ExportOptions(keepExif=exif,keepLocation=gps,keepCaptureTime=time))
                val result=output.readBytes();val string=result.toString(Charsets.ISO_8859_1)
                assertEquals(bytes.size,result.size)
                assertEquals(exif,string.contains("MODEL_SAMPLE"));assertEquals(gps,string.contains("LOCATION_SECRET"));assertEquals(time,string.contains("TIME_SECRET"))
                val mediaStart=bytes.toString(Charsets.ISO_8859_1).indexOf("mdat")+4
                assertArrayEquals(media,result.copyOfRange(mediaStart,mediaStart+media.size))
                val headerStart=string.indexOf("mvhd")+8
                assertArrayEquals(if(time)created else ByteArray(8),result.copyOfRange(headerStart,headerStart+8))
                if(exif&&gps&&time)assertArrayEquals(bytes,result)
            } finally { source.delete();output.delete() }
        }
    }
    @Test fun rejectsTimedMetadataTracksAndCorruptBoxesWhenRemovingPrivateFields() {
        val source=File.createTempFile("motion-invalid", ".mp4");val output=File.createTempFile("motion-clean", ".mp4")
        try {
            source.writeBytes(fixture(handler="meta"))
            assertThrows(IllegalArgumentException::class.java) { VideoMetadata.copy(source,0,source.length(),output,ExportOptions()) }
            source.writeBytes(fixture()+int(100)+text("junk"))
            assertThrows(IllegalArgumentException::class.java) { VideoMetadata.copy(source,0,source.length(),output,ExportOptions()) }
        } finally { source.delete();output.delete() }
    }
    @Test fun knownLegacyLocationDatesAndUnknownVendorMetadataAreCleared() {
        val source=File.createTempFile("motion-legacy", ".mp4");val output=File.createTempFile("motion-clean", ".mp4")
        try {
            val extra=box("udta",box("©xyz",text("LEGACY_GPS"))+box("©day",text("LEGACY_DATE"))+box("zzzz",text("UNKNOWN_PRIVATE")))
            source.writeBytes(fixture()+extra)
            VideoMetadata.copy(source,0,source.length(),output,ExportOptions(keepLocation=false,keepCaptureTime=false))
            val result=output.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(result.contains("LEGACY_GPS"));assertFalse(result.contains("LEGACY_DATE"));assertFalse(result.contains("UNKNOWN_PRIVATE"))
        } finally { source.delete();output.delete() }
    }
}
