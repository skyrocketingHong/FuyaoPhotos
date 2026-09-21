package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.model.*
import ing.fuyaoskyrocket.photoinfo.domain.metadata.ExportMetadata
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneOffset

class ExportOptionsTest {
    private val tags=mapOf("Model" to "Camera", "GPSLatitude" to "30/1,0/1,0/1", "DateTimeOriginal" to "2024:01:02 12:00:00",
        "OffsetTimeOriginal" to "+08:00", "GPSDateStamp" to "2024:01:02", "MakerNote" to "secret", "BodySerialNumber" to "secret")
    @Test fun allEightMetadataChoicesRemainIndependent() {
        for(exif in listOf(false,true))for(gps in listOf(false,true))for(time in listOf(false,true)) {
            val result=ExportMetadata.select(tags,ExportOptions(keepExif=exif,keepLocation=gps,keepCaptureTime=time))
            assertEquals(exif,result.containsKey("Model"));assertEquals(gps,result.containsKey("GPSLatitude"))
            assertEquals(time,result.containsKey("DateTimeOriginal"));assertEquals(gps&&time,result.containsKey("GPSDateStamp"))
            assertFalse(result.containsKey("MakerNote"));assertFalse(result.containsKey("BodySerialNumber"))
        }
    }
    @Test fun timestampsHonorOffsetsSubsecondsAndInvalidValues() {
        assertEquals(1704168000000L,ExportMetadata.capturedAt(tags,ZoneOffset.UTC))
        assertEquals(1704168000123L,ExportMetadata.capturedAt(tags+mapOf("SubSecTimeOriginal" to "1234"),ZoneOffset.UTC))
        assertNull(ExportMetadata.capturedAt(tags+mapOf("DateTimeOriginal" to "2024:02:30 12:00:00")))
        assertNull(ExportMetadata.capturedAt(tags+mapOf("OffsetTimeOriginal" to "bad")))
        assertNull(ExportMetadata.capturedAt(emptyMap()))
    }
    @Test fun temporaryChoicesCannotMutateDefaultsAndRestoreIsBounded() {
        val defaults=ExportOptions(ExportFormat.PNG,82,false,true,false)
        val temporary=defaults.copy(jpegQuality=12,keepLocation=false)
        assertEquals(82,defaults.jpegQuality);assertTrue(defaults.keepLocation)
        assertEquals(temporary,ExportOptions.restore(temporary.fields()))
        assertEquals(ExportOptions(),ExportOptions.restore(listOf("invalid")))
        assertEquals(ExportFormat.JPEG,defaults.sanitized(jpegRequired=true).format)
        assertEquals(100,defaults.copy(jpegQuality=1000).sanitized().jpegQuality)
    }
}
