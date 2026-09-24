package ing.fuyaoskyrocket.photoinfo.domain.media

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Small, bounded ISOBMFF reader used to check whether an HEIC has an ISO gain-map graph. */
object HeifGraph {
    private const val MAX_FILE_BYTES = 512L * 1024 * 1024
    private const val MAX_META_BYTES = 16L * 1024 * 1024
    private const val MAX_BOXES = 4_096

    data class MotionPayload(val offset: Long, val length: Long, val headerBytes: Long)

    data class Report(
        val primaryItemId: Int,
        val toneMapItemId: Int?,
        val hasIsoGainMap: Boolean,
        val hasStyleMetadata: Boolean,
        val hasPortraitMetadata: Boolean,
        val hasUnsupportedItems: Boolean,
        val motionPayload: MotionPayload?,
    )

    private data class Box(val type: String, val start: Long, val dataStart: Long, val end: Long) {
        val size get() = end - start
    }
    private data class Item(val id: Int, val type: String, val contentType: String?)
    private data class Reference(val type: String, val from: Int, val to: List<Int>)

    @Throws(IOException::class)
    fun inspect(file: File): Report = RandomAccessFile(file, "r").use { source ->
        val length = source.length()
        if (length < 16 || length > MAX_FILE_BYTES) throw IOException("HEIF size is unsupported")
        val top = boxes(source, 0, length)
        if (top.firstOrNull()?.type != "ftyp" || top.count { it.type == "ftyp" } != 1) {
            throw IOException("HEIF file type must be first and unique")
        }
        if (top.any { it.type !in setOf("ftyp", "meta", "mdat", "free", "skip", "mpvd") }) {
            throw IOException("Unknown HEIF top-level data")
        }
        val motionBox = top.filter { it.type == "mpvd" }.let { matches ->
            if (matches.size > 1) throw IOException("Multiple HEIF motion payloads")
            matches.singleOrNull()
        }
        if (motionBox != null && top.last() != motionBox) throw IOException("HEIF motion payload is not last")
        val motionPayload = motionBox?.let { MotionPayload(it.dataStart, it.end - it.dataStart, it.dataStart - it.start) }
        val ftyp = top.firstOrNull { it.type == "ftyp" } ?: throw IOException("HEIF file type missing")
        if (ftyp.size < 16 || ftyp.size > 1024) throw IOException("HEIF file type is invalid")
        val brands = bytes(source, ftyp.dataStart, ftyp.end - ftyp.dataStart)
        val allowedBrands = setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1")
        val isHeif = (0..brands.size - 4 step 4).any { index ->
            brands.copyOfRange(index, index + 4).toString(Charsets.US_ASCII) in allowedBrands
        }
        if (!isHeif) throw IOException("Unsupported HEIF brand")
        val meta = top.singleOrNull { it.type == "meta" } ?: throw IOException("HEIF metadata missing")
        if (meta.end - meta.dataStart !in 4..MAX_META_BYTES) throw IOException("HEIF metadata exceeds limit")
        val children = boxes(source, meta.dataStart + 4, meta.end)
        val items = children.firstOrNull { it.type == "iinf" }?.let { readItems(source, it) }.orEmpty()
        val itemIds = items.mapTo(mutableSetOf()) { it.id }
        if (itemIds.size != items.size) throw IOException("Duplicate HEIF item identifier")
        val primary = children.firstOrNull { it.type == "pitm" }?.let { readPrimary(source, it) }
            ?: throw IOException("HEIF primary image missing")
        if (primary !in itemIds) throw IOException("HEIF primary image is unavailable")
        val toneMaps = items.filter { it.type == "tmap" }
        if (toneMaps.size > 1) throw IOException("Ambiguous tone maps")
        val toneMap = toneMaps.singleOrNull()?.id
        val references = children.firstOrNull { it.type == "iref" }?.let { readReferences(source, it) }.orEmpty()
        val toneMapReference = toneMap != null && references.any { ref ->
            ref.type == "dimg" && ref.from == toneMap && primary in ref.to && ref.to.all { it in itemIds }
        }
        val auxiliary = children.firstOrNull { it.type == "iprp" }?.let { auxiliaryTypes(source, it) } ?: emptyList()
        val hasAux = auxiliary.any { it == "urn:iso:std:iso:ts:21496:-1" }
        val styles = items.any { it.type.contains("style", true) || it.contentType?.contains("styleMetadata", true) == true }
        val portrait = items.any {
            it.type.contains("depth", true) || it.contentType?.contains("portrait", true) == true
        }
        val xmpItems = items.count { it.type == "mime" && it.contentType in setOf("application/rdf+xml", "application/xmp+xml") }
        val unsupportedItems = auxiliary.isNotEmpty() || xmpItems > 1 ||
            children.firstOrNull { it.type == "iprp" }?.let { hasUnknownProperties(source, it) } == true || items.any { item ->
            item.type !in setOf("hvc1", "grid", "Exif") &&
                !(motionPayload != null && item.type == "mime" &&
                    item.contentType in setOf("application/rdf+xml", "application/xmp+xml"))
        } ||
            references.any { it.type !in setOf("dimg", "thmb", "cdsc") || it.from !in itemIds || it.to.any { target -> target !in itemIds } }
        Report(primary, toneMap, hasAux && toneMapReference, styles, portrait, unsupportedItems, motionPayload)
    }

    private fun readPrimary(source: RandomAccessFile, box: Box): Int {
        if (box.end - box.dataStart < 6) throw IOException("HEIF primary item is truncated")
        source.seek(box.dataStart)
        val version = source.readUnsignedByte()
        source.skipBytes(3)
        return if (version == 0) source.readUnsignedShort() else if (version == 1) {
            if (box.end - source.filePointer < 4) throw IOException("HEIF primary item is truncated")
            source.readInt()
        } else throw IOException("Unsupported primary item version")
    }

    private fun readItems(source: RandomAccessFile, box: Box): List<Item> {
        if (box.end - box.dataStart < 6) throw IOException("HEIF item list is truncated")
        source.seek(box.dataStart)
        val version = source.readUnsignedByte()
        source.skipBytes(3)
        val count = if (version == 0) source.readUnsignedShort() else if (version == 1) source.readInt() else
            throw IOException("Unsupported HEIF item list version")
        if (count !in 0..MAX_BOXES) throw IOException("HEIF item count exceeds limit")
        val result = boxes(source, source.filePointer, box.end).filter { it.type == "infe" }
        if (count != result.size) throw IOException("HEIF item count differs from the list")
        return result.map { entry ->
            source.seek(entry.dataStart)
            val itemVersion = source.readUnsignedByte()
            source.skipBytes(3)
            if (itemVersion !in 2..3) throw IOException("Unsupported HEIF item version")
            val id = if (itemVersion == 2) source.readUnsignedShort() else source.readInt()
            source.readUnsignedShort()
            if (source.filePointer + 4 > entry.end) throw IOException("HEIF item type is truncated")
            val type = fourCc(source)
            val content = if (type == "mime") {
                val value = bytes(source, source.filePointer, minOf(entry.end - source.filePointer, 256))
                val nameEnd = value.indexOf(0)
                if (nameEnd < 0) throw IOException("HEIF MIME item name is unterminated")
                val contentStart = nameEnd + 1
                if (contentStart >= value.size) throw IOException("HEIF MIME type is missing")
                val contentEnd = value.indices.firstOrNull { it >= contentStart && value[it] == 0.toByte() } ?: value.size
                value.copyOfRange(contentStart, contentEnd).toString(Charsets.US_ASCII)
            } else null
            Item(id, type, content)
        }
    }

    private fun readReferences(source: RandomAccessFile, box: Box): List<Reference> {
        if (box.end - box.dataStart < 4) throw IOException("HEIF references are truncated")
        source.seek(box.dataStart)
        val version = source.readUnsignedByte()
        source.skipBytes(3)
        if (version !in 0..1) throw IOException("Unsupported HEIF reference version")
        val width = if (version == 0) 2 else 4
        return boxes(source, source.filePointer, box.end).map { ref ->
            source.seek(ref.dataStart)
            if (ref.end - ref.dataStart < width + 2) throw IOException("HEIF reference is truncated")
            val from = if (width == 2) source.readUnsignedShort() else source.readInt()
            val count = source.readUnsignedShort()
            if (count > MAX_BOXES || ref.end - source.filePointer != count.toLong() * width) {
                throw IOException("HEIF reference targets are invalid")
            }
            val to = List(count) { if (width == 2) source.readUnsignedShort() else source.readInt() }
            Reference(ref.type, from, to)
        }
    }

    private fun auxiliaryTypes(source: RandomAccessFile, iprp: Box): List<String> {
        val ipco = boxes(source, iprp.dataStart, iprp.end).singleOrNull { it.type == "ipco" } ?: return emptyList()
        return boxes(source, ipco.dataStart, ipco.end).filter { it.type == "auxC" }.map { box ->
            if (box.end - box.dataStart < 5) throw IOException("HEIF auxiliary type is truncated")
            val payload = bytes(source, box.dataStart + 4, minOf(box.end - box.dataStart - 4, 256))
            val end = payload.indexOf(0)
            if (end < 0) throw IOException("HEIF auxiliary type is unterminated")
            payload.copyOfRange(0, end).toString(Charsets.US_ASCII)
        }
    }

    private fun hasUnknownProperties(source: RandomAccessFile, iprp: Box): Boolean {
        val ipco = boxes(source, iprp.dataStart, iprp.end).singleOrNull { it.type == "ipco" } ?: return false
        val ordinary = setOf("ispe", "pixi", "hvcC", "colr", "clli", "pasp", "irot", "imir", "clap", "rloc")
        return boxes(source, ipco.dataStart, ipco.end).any { it.type !in ordinary && it.type != "auxC" }
    }

    private fun boxes(source: RandomAccessFile, start: Long, end: Long): List<Box> {
        if (start < 0 || end > source.length() || end < start) throw IOException("Invalid HEIF box range")
        val result = ArrayList<Box>()
        var position = start
        while (position < end) {
            if (end - position < 8 || result.size >= MAX_BOXES) throw IOException("Truncated or excessive HEIF boxes")
            source.seek(position)
            val compact = source.readInt().toLong() and 0xffffffffL
            val type = fourCc(source)
            val header = if (compact == 1L) 16L else 8L
            val length = when (compact) {
                0L -> end - position
                1L -> if (end - position >= 16) source.readLong() else throw IOException("Truncated HEIF large box")
                else -> compact
            }
            if (length < header || length > end - position) throw IOException("Invalid HEIF box size")
            result += Box(type, position, position + header, position + length)
            position += length
        }
        return result
    }

    private fun bytes(source: RandomAccessFile, at: Long, length: Long): ByteArray {
        if (length < 0 || length > MAX_META_BYTES || at < 0 || length > source.length() - at) {
            throw IOException("HEIF metadata range is invalid")
        }
        return ByteArray(length.toInt()).also { source.seek(at); source.readFully(it) }
    }

    private fun fourCc(source: RandomAccessFile): String = ByteArray(4).also(source::readFully).toString(Charsets.US_ASCII)
}
