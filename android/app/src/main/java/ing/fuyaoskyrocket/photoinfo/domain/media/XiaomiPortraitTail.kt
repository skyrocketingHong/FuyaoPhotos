package ing.fuyaoskyrocket.photoinfo.domain.media

import ing.fuyaoskyrocket.photoinfo.domain.model.ExportOptions
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.io.StringWriter
import java.nio.ByteOrder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** Keeps Xiaomi's portrait image/depth tail at the same byte length while filtering known JPEG metadata. */
object XiaomiPortraitTail {
    private val signature = "MCBOKEHSOT".toByteArray(Charsets.US_ASCII)
    private val exif = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)
    private val xmp = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
    private const val MAX_TAIL = 64 * 1024 * 1024

    private data class Segment(val marker: Int, val markerAt: Int, val dataAt: Int, val end: Int)
    data class Layout(val firstEnd: Int, val secondEnd: Int, val segments: List<SegmentRange>)
    data class SegmentRange(val marker: Int, val markerAt: Int, val dataAt: Int, val end: Int)

    fun read(file: File, part: JpegContainer.Part): ByteArray {
        require(part.offset >= 0 && part.length in 1..MAX_TAIL &&
            part.offset + part.length == file.length()) { "Invalid Xiaomi portrait tail range" }
        return ByteArray(part.length.toInt()).also { bytes ->
            RandomAccessFile(file, "r").use { input -> input.seek(part.offset); input.readFully(bytes) }
        }
    }

    fun layout(bytes: ByteArray): Layout {
        require(bytes.size in 1..MAX_TAIL)
        val first = jpeg(bytes, 0)
        val second = jpeg(bytes, first.first)
        require(second.first + signature.size <= bytes.size &&
            signature.indices.all { bytes[second.first + it] == signature[it] }) {
            "Xiaomi portrait trailer is missing"
        }
        return Layout(first.first, second.first,
            (first.second + second.second).map { SegmentRange(it.marker, it.markerAt, it.dataAt, it.end) })
    }

    fun writeFiltered(source: File, part: JpegContainer.Part, output: OutputStream, options: ExportOptions): ByteArray {
        val bytes = read(source, part)
        val found = layout(bytes)
        if (!options.keepExif || !options.keepLocation || !options.keepCaptureTime) {
            found.segments.filter { it.marker == 0xe1 }.forEach { segment ->
                when {
                    bytes.startsAt(segment.dataAt, exif) -> {
                        if (!options.keepExif) {
                            bytes[segment.markerAt + 1] = 0xef.toByte()
                            bytes.fill(0, segment.dataAt, segment.end)
                        } else {
                            // Vendor EXIF that cannot be rewritten in place degrades to full removal;
                            // the segment length stays untouched so the depth layout is preserved.
                            runCatching {
                                scrubExif(bytes, segment.dataAt + exif.size, segment.end,
                                    options.keepLocation, options.keepCaptureTime)
                            }.onFailure {
                                bytes.fill(0, segment.dataAt + exif.size, segment.end)
                            }
                        }
                    }
                    bytes.startsAt(segment.dataAt, xmp) -> {
                        scrubXmp(bytes, segment.dataAt + xmp.size, segment.end,
                            options.keepLocation, options.keepCaptureTime)
                    }
                }
            }
        }
        check(bytes.size == part.length.toInt() && layout(bytes).secondEnd == found.secondEnd)
        output.write(bytes)
        return bytes
    }

    private fun jpeg(bytes: ByteArray, start: Int): Pair<Int, List<Segment>> {
        require(start >= 0 && start + 2 <= bytes.size && bytes[start] == 0xff.toByte() && bytes[start + 1] == 0xd8.toByte())
        val segments = ArrayList<Segment>()
        var at = start + 2
        var scan = false
        var count = 0
        while (at < bytes.size) {
            require(++count <= 100_000) { "Excessive portrait JPEG markers" }
            val markerAt: Int
            val marker: Int
            if (scan) {
                while (at < bytes.size && bytes[at] != 0xff.toByte()) at++
                require(at < bytes.size)
                markerAt = at
                while (at < bytes.size && bytes[at] == 0xff.toByte()) at++
                require(at < bytes.size)
                marker = bytes[at++].toInt() and 0xff
                if (marker == 0 || marker in 0xd0..0xd7) continue
            } else {
                require(bytes[at] == 0xff.toByte()) { "Invalid portrait JPEG marker" }
                markerAt = at
                while (at < bytes.size && bytes[at] == 0xff.toByte()) at++
                require(at < bytes.size)
                marker = bytes[at++].toInt() and 0xff
            }
            if (marker == 0xd9) return at to segments
            require(marker != 0xd8 && marker != 0 && marker != 1 && marker !in 0xd0..0xd7)
            require(at + 2 <= bytes.size)
            val size = ((bytes[at].toInt() and 0xff) shl 8) or (bytes[at + 1].toInt() and 0xff)
            require(size >= 2 && size <= bytes.size - at)
            val dataAt = at + 2
            val end = at + size
            if (marker in 0xe0..0xef) segments += Segment(marker, markerAt, dataAt, end)
            at = end
            scan = marker == 0xda
        }
        throw IOException("Portrait JPEG is truncated")
    }

    private fun scrubExif(bytes: ByteArray, start: Int, end: Int, keepLocation: Boolean, keepTime: Boolean) {
        require(end - start >= 8)
        val order = when {
            bytes[start] == 0x49.toByte() && bytes[start + 1] == 0x49.toByte() -> ByteOrder.LITTLE_ENDIAN
            bytes[start] == 0x4d.toByte() && bytes[start + 1] == 0x4d.toByte() -> ByteOrder.BIG_ENDIAN
            else -> throw IOException("Portrait EXIF byte order is invalid")
        }
        fun u16(at: Int): Int {
            require(at >= start && at + 2 <= end)
            val a = bytes[at].toInt() and 0xff
            val b = bytes[at + 1].toInt() and 0xff
            return if (order == ByteOrder.BIG_ENDIAN) (a shl 8) or b else (b shl 8) or a
        }
        fun u32(at: Int): Long {
            require(at >= start && at + 4 <= end)
            return if (order == ByteOrder.BIG_ENDIAN) {
                ((u16(at).toLong() shl 16) or u16(at + 2).toLong())
            } else {
                u16(at).toLong() or (u16(at + 2).toLong() shl 16)
            }
        }
        fun absolute(relative: Long, length: Long): Int {
            require(relative >= 0 && length >= 0 && relative <= end - start - length)
            return start + relative.toInt()
        }
        fun eraseValue(entry: Int) {
            val type = u16(entry + 2)
            val count = u32(entry + 4)
            val unit = when (type) { 1, 2, 6, 7 -> 1L; 3, 8 -> 2L; 4, 9, 11 -> 4L; 5, 10, 12 -> 8L; else -> 0L }
            require(unit > 0 && count <= (end - start) / unit)
            val size = count * unit
            if (size > 4) {
                val dataAt = absolute(u32(entry + 8), size)
                bytes.fill(0, dataAt, dataAt + size.toInt())
            }
            bytes.fill(0, entry, entry + 12)
        }
        fun ifd(relative: Long): Pair<List<Int>, Long> {
            val base = absolute(relative, 2)
            val count = u16(base)
            require(count <= 512)
            val endOfEntries = base.toLong() + 2 + count.toLong() * 12
            require(endOfEntries + 4 <= end)
            return (0 until count).map { base + 2 + it * 12 } to u32(endOfEntries.toInt())
        }
        require(u16(start + 2) == 42)
        val (ifd0, nextIfd) = ifd(u32(start + 4))
        val gps = ifd0.firstOrNull { u16(it) == 0x8825 }
        val exifIfd = ifd0.firstOrNull { u16(it) == 0x8769 }
        val timeTags = setOf(0x0132, 0x9003, 0x9004, 0x9010, 0x9011, 0x9012, 0x9290, 0x9291, 0x9292)
        if (!keepTime) {
            ifd0.filter { u16(it) in timeTags }.forEach(::eraseValue)
            if (exifIfd != null) ifd(u32(exifIfd + 8)).first
                .filter { u16(it) in timeTags }.forEach(::eraseValue)
            if (nextIfd != 0L) ifd(nextIfd).first.filter { u16(it) in timeTags }.forEach(::eraseValue)
        }
        if (gps != null) {
            val gpsEntries = ifd(u32(gps + 8)).first
            if (!keepLocation) {
                gpsEntries.forEach(::eraseValue)
                bytes.fill(0, gps, gps + 12)
            } else if (!keepTime) gpsEntries.filter { u16(it) in setOf(0x0007, 0x001d) }.forEach(::eraseValue)
        }
    }

    private fun scrubXmp(bytes: ByteArray, start: Int, end: Int, keepLocation: Boolean, keepTime: Boolean) {
        if (keepLocation && keepTime) return
        require(end - start in 1..128 * 1024)
        val original = bytes.copyOfRange(start, end).toString(Charsets.UTF_8)
        fun sensitive(name: String): Boolean {
            val lower = name.lowercase(java.util.Locale.ROOT)
            return (!keepLocation && listOf("gps", "latitude", "longitude", "location", "altitude").any(lower::contains)) ||
                (!keepTime && listOf("date", "time").any(lower::contains))
        }
        if (!listOf("GPS", "Latitude", "Longitude", "Location", "Altitude", "Date", "Time")
                .any { original.contains(it, true) }) return
        require(!original.contains("<!DOCTYPE", true) && !original.contains("<!ENTITY", true))
        val rewritten = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            val builder = factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> InputSource(StringReader("")) }
            }
            val doc = builder.parse(ByteArrayInputStream(original.toByteArray(Charsets.UTF_8)))
            val nodes = doc.getElementsByTagName("*")
            val elements = List(nodes.length) { nodes.item(it) as Element }
            for (element in elements.asReversed()) {
                val attributes = List(element.attributes.length) { element.attributes.item(it) }
                attributes.filter { sensitive(it.localName ?: it.nodeName) }
                    .forEach { attribute ->
                        if (attribute.namespaceURI != null && attribute.localName != null)
                            element.removeAttributeNS(attribute.namespaceURI, attribute.localName)
                        else element.removeAttribute(attribute.nodeName)
                    }
                if (sensitive(element.localName ?: element.tagName)) element.parentNode?.removeChild(element)
            }
            val transformed = StringWriter()
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes")
            }.transform(DOMSource(doc), StreamResult(transformed))
            transformed.toString().toByteArray(Charsets.UTF_8)
        }.getOrNull()
        // The region length is fixed by the depth layout; anything that cannot be rewritten in
        // place degrades to a minimal empty packet padded with spaces instead of failing export.
        val fallback = ("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF " +
            "xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"><rdf:Description/></rdf:RDF></x:xmpmeta>")
            .toByteArray(Charsets.UTF_8)
        val payload = when {
            rewritten != null && rewritten.size <= end - start -> rewritten
            fallback.size <= end - start -> fallback
            else -> ByteArray(end - start)
        }
        bytes.fill(' '.code.toByte(), start, end)
        payload.copyInto(bytes, start)
    }

    private fun ByteArray.startsAt(offset: Int, prefix: ByteArray): Boolean =
        offset >= 0 && offset + prefix.size <= size && prefix.indices.all { this[offset + it] == prefix[it] }
}
