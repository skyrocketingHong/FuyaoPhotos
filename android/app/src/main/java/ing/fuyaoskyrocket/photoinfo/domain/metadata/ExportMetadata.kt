package ing.fuyaoskyrocket.photoinfo.domain.metadata

import ing.fuyaoskyrocket.photoinfo.domain.model.ExportOptions
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** ASVS 14.2.8: separate explicit opt-ins; never copy arbitrary EXIF or vendor payloads. */
object ExportMetadata {
    val captureTags = setOf("Make", "Model", "LensMake", "LensModel", "ExposureTime", "FNumber",
        "PhotographicSensitivity", "FocalLength", "FocalLengthIn35mmFilm", "WhiteBalance", "Flash")
    val timeTags = setOf("DateTime", "DateTimeOriginal", "DateTimeDigitized", "OffsetTime", "OffsetTimeOriginal",
        "OffsetTimeDigitized", "SubSecTime", "SubSecTimeOriginal", "SubSecTimeDigitized")
    val locationTags = setOf("GPSVersionID", "GPSLatitude", "GPSLatitudeRef", "GPSLongitude", "GPSLongitudeRef",
        "GPSAltitude", "GPSAltitudeRef", "GPSHPositioningError", "GPSMapDatum")
    val gpsTimeTags = setOf("GPSDateStamp", "GPSTimeStamp")
    val readableTags = captureTags + timeTags + locationTags + gpsTimeTags
    fun select(tags: Map<String, String>, options: ExportOptions): Map<String, String> = tags.filterKeys { tag ->
        (options.keepExif && tag in captureTags) || (options.keepCaptureTime && tag in timeTags) ||
            (options.keepLocation && tag in locationTags) || (options.keepLocation && options.keepCaptureTime && tag in gpsTimeTags)
    }
    fun capturedAt(tags: Map<String, String>, zone: ZoneId = ZoneId.systemDefault()): Long? = runCatching {
        val raw = tags["DateTimeOriginal"] ?: return null
        val local = LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT))
        val offset = tags["OffsetTimeOriginal"]?.let(ZoneOffset::of)
        val instant = if (offset != null) local.toInstant(offset) else local.atZone(zone).toInstant()
        val millis = tags["SubSecTimeOriginal"]?.takeIf { it.matches(Regex("[0-9]{1,9}")) }?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
        instant.toEpochMilli() + millis
    }.getOrNull()?.takeIf { it > 0L }
}
