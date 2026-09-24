package ing.fuyaoskyrocket.photoinfo.domain.media

import ing.fuyaoskyrocket.photoinfo.domain.media.IsoBmff.box
import ing.fuyaoskyrocket.photoinfo.domain.media.IsoBmff.data
import ing.fuyaoskyrocket.photoinfo.domain.media.IsoBmff.full
import java.io.File

/** Rebuilds item offsets and associations without changing encoded image samples. */
internal data class HeifImageContainer(
    val avif: Boolean,
    val primary: Int,
    val items: List<Item>,
    val properties: List<ByteArray>,
    val references: List<Reference>,
) {
    data class Property(val index: Int, val essential: Boolean)
    data class Item(val id: Int, val type: String, val infoSuffix: ByteArray, val payload: ByteArray,
        val properties: List<Property> = emptyList(), val hidden: Boolean = false)
    data class Reference(val type: String, val from: Int, val to: List<Int>)
    val bitDepth: Int get() = properties.mapNotNull { prop ->
        when(String(prop,4,4,Charsets.US_ASCII)) {
            "pixi" -> {
                require(prop.size >= 13)
                val channels=prop[12].toInt() and 255
                require(channels in 1..4 && prop.size==13+channels)
                prop.drop(13).maxOf { it.toInt() and 255 }
            }
            "av1C" -> { require(prop.size>=12); if(prop[10].toInt() and 0x40==0)8 else if(prop[10].toInt() and 0x20==0)10 else 12 }
            "hvcC" -> { require(prop.size>=31); 8 + (prop[25].toInt() and 7) }
            else -> null
        }
    }.maxOrNull() ?: 8
    val hdrTransferCode: Int get() = properties.firstOrNull { prop ->
        prop.size>=19 && String(prop,4,8,Charsets.US_ASCII)=="colrnclx" &&
            (((prop[14].toInt() and 255) shl 8) or (prop[15].toInt() and 255)) in setOf(16,18)
    }?.let { ((it[14].toInt() and 255) shl 8) or (it[15].toInt() and 255) } ?: 0
    val hdrTransfer: Boolean get() = hdrTransferCode!=0

    fun withColorSpace(primaries: Int, transfer: Int): HeifImageContainer {
        val color = properties.indexOfFirst { it.size>=19 && String(it,4,8,Charsets.US_ASCII)=="colrnclx" }
        if(color<0) {
            // HeifWriter configures full-range BT.601 (8-bit) / BT.2020 (10-bit), but some muxers omit colr.
            val property=box("colr",data {
                writeBytes("nclx");writeShort(primaries);writeShort(transfer)
                writeShort(if(bitDepth>8)9 else 6);writeByte(0x80)
            })
            val index=properties.size+1
            return copy(properties=properties+listOf(property),items=items.map { item ->
                if(item.type in setOf("grid","hvc1","av01"))item.copy(properties=item.properties+Property(index,false)) else item
            })
        }
        return copy(properties=properties.mapIndexed { index, raw ->
            if(index!=color)raw else raw.copyOf().also { bytes ->
                java.nio.ByteBuffer.wrap(bytes).putShort(12,primaries.toShort()).putShort(14,transfer.toShort())
            }
        })
    }

    fun gainMap(): Pair<Int, IsoGainMapMetadata>? {
        val tone = items.singleOrNull { it.type == "tmap" } ?: return null
        val targets = references.single { it.type == "dimg" && it.from == tone.id }.to
        require(targets.size == 2 && targets[0] == primary && targets[1] != primary)
        return targets[1] to IsoGainMapMetadata.parse(tone.payload)
    }

    fun validateEditable() {
        require(bitDepth in 8..10 && (avif || bitDepth==8)) { "Unsupported image precision" }
        val gain = gainMap()
        require(items.count { it.type == "tmap" } <= 1)
        require(items.none { it.type == "mime" }) { "Unsupported attached metadata" }
        require(references.all { it.type in setOf("dimg", "cdsc", "thmb", "auxl") })
        properties.forEach { property ->
            val type = String(property, 4, 4, Charsets.US_ASCII)
            require(type in setOf("ispe", "pixi", "hvcC", "av1C", "colr", "clli", "mdcv", "pasp", "irot", "imir", "clap", "rloc", "auxC"))
            if (type == "auxC") require(gain != null && String(property, 12, property.size - 12, Charsets.US_ASCII) ==
                "urn:iso:std:iso:ts:21496:-1\u0000") { "Unsupported auxiliary image" }
            if (type == "colr" && property.size >= 19 && String(property, 8, 4, Charsets.US_ASCII) == "nclx") {
                val transfer = ((property[14].toInt() and 255) shl 8) or (property[15].toInt() and 255)
                require(transfer !in setOf(16, 18) || (avif && bitDepth==10)) { "HDR transfer requires 10-bit AVIF" }
            }
        }
        require(references.filter { it.type == "auxl" }.all { it.from == gain?.first && it.to.all { target ->
            target == primary || items.any { item -> item.id == target && item.type == "tmap" }
        } })
    }

    fun standalone(id: Int): HeifImageContainer {
        val selected = mutableSetOf<Int>()
        fun collect(item: Int) {
            require(selected.size <= 4096)
            if (!selected.add(item)) return
            references.filter { it.from == item && it.type == "dimg" }.flatMap { it.to }.forEach(::collect)
        }
        collect(id)
        return copy(primary = id, items = items.filter { it.id in selected }.map { item ->
            item.copy(hidden = item.id != id, properties = item.properties.filter {
                String(properties[it.index - 1], 4, 4, Charsets.US_ASCII) != "auxC"
            })
        }, references = references.filter { it.from in selected && it.to.all(selected::contains) && it.type == "dimg" })
    }

    fun withGainMap(gain: HeifImageContainer, metadata: IsoGainMapMetadata): HeifImageContainer {
        require(avif == gain.avif && items.none { it.type == "tmap" })
        val base = items.single { it.id == primary }
        val first = items.maxOf { it.id } + 1
        val ids = gain.items.mapIndexed { index, item -> item.id to first + index }.toMap()
        val gainID = ids.getValue(gain.primary)
        val toneID = first + gain.items.size
        val auxIndex = properties.size + gain.properties.size + 1
        val aux = full("auxC", payload = "urn:iso:std:iso:ts:21496:-1\u0000".toByteArray())
        val imported = gain.items.map { item -> item.copy(id = ids.getValue(item.id), hidden = true,
            properties = item.properties.map { it.copy(index = it.index + properties.size) } +
                if (item.id == gain.primary) listOf(Property(auxIndex, true)) else emptyList()) }
        val toneProperties = base.properties.filter { prop ->
            String(properties[prop.index - 1], 4, 4, Charsets.US_ASCII) in setOf("ispe", "pixi", "colr")
        }
        val tone = Item(toneID, "tmap", byteArrayOf(0), metadata.strictToneMap(), toneProperties)
        return copy(items = items + imported + tone, properties = properties + gain.properties + listOf(aux),
            references = references + gain.references.map { ref ->
                ref.copy(from = ids.getValue(ref.from), to = ref.to.map(ids::getValue))
            } + listOf(Reference("dimg", toneID, listOf(primary, gainID)),
                Reference("auxl", gainID, listOf(primary, toneID))))
    }

    fun withAuxiliary(auxiliary: HeifImageContainer, type: String, xmp: String): HeifImageContainer {
        require(!avif && !auxiliary.avif && xmp.length <= 32_768)
        val first = items.maxOf { it.id } + 1
        val ids = auxiliary.items.mapIndexed { index, item -> item.id to first + index }.toMap()
        val auxiliaryID = ids.getValue(auxiliary.primary)
        val metadataID = first + auxiliary.items.size
        val auxIndex = properties.size + auxiliary.properties.size + 1
        val props = properties + auxiliary.properties + listOf(full("auxC", payload = (type + "\u0000").toByteArray()))
        val added = auxiliary.items.map { item -> item.copy(id = ids.getValue(item.id), hidden = true,
            properties = item.properties.map { it.copy(index = it.index + properties.size) } +
                if (item.id == auxiliary.primary) listOf(Property(auxIndex, true)) else emptyList()) }
        return copy(items = items + added + Item(metadataID, "mime", "\u0000application/rdf+xml\u0000\u0000".toByteArray(), xmp.toByteArray(), hidden = true),
            properties = props, references = references + auxiliary.references.map {
                it.copy(from = ids.getValue(it.from), to = it.to.map(ids::getValue))
            } + Reference("auxl", auxiliaryID, listOf(primary) + items.filter { it.type == "tmap" }.map { it.id }) +
                Reference("cdsc", metadataID, listOf(auxiliaryID)))
    }

    fun write(file: File) {
        require(items.size in 1..4096 && items.map { it.id }.distinct().size == items.size)
        require(items.all { it.id in 1..65535 && it.properties.size <= 255 })
        require(properties.size < 32768)
        val ftyp = box("ftyp", data {
            writeBytes(if (avif) "avif" else "heic"); writeInt(0)
            writeBytes(if (avif) "avifmif1miaf" else "mif1heic")
        })
        fun meta(payloadAt: Int): ByteArray {
            val hdlr = full("hdlr", payload = data { writeInt(0); writeBytes("pict"); write(ByteArray(12)); writeByte(0) })
            val pitm = full("pitm", payload = data { writeShort(primary) })
            val iinf = full("iinf", payload = data {
                writeShort(items.size)
                items.forEach { item -> write(full("infe", 2, if (item.hidden) 1 else 0, data {
                    writeShort(item.id); writeShort(0); writeBytes(item.type); write(item.infoSuffix)
                })) }
            })
            val iloc = full("iloc", payload = data {
                writeByte(0x44); writeByte(0); writeShort(items.size)
                var at = payloadAt.toLong()
                items.forEach { item ->
                    writeShort(item.id); writeShort(0); writeShort(1); writeInt(at.toInt()); writeInt(item.payload.size)
                    at += item.payload.size
                    require(at <= IsoBmff.MAX_BYTES)
                }
            })
            val iref = full("iref", payload = data { references.forEach { ref ->
                write(box(ref.type, data { writeShort(ref.from); writeShort(ref.to.size); ref.to.forEach(::writeShort) }))
            } })
            val ipma = full("ipma", flags = 1, payload = data {
                writeInt(items.size)
                items.forEach { item ->
                    writeShort(item.id); writeByte(item.properties.size)
                    item.properties.forEach { writeShort(it.index or if (it.essential) 0x8000 else 0) }
                }
            })
            return full("meta", payload = data {
                write(hdlr); write(pitm); write(iinf); write(iloc); write(iref)
                write(box("iprp", box("ipco", *properties.toTypedArray()), ipma))
                items.singleOrNull { it.type == "tmap" }?.let { tone ->
                    write(box("grpl", full("altr", payload = data {
                        writeInt(items.maxOf { it.id } + 1); writeInt(2); writeInt(tone.id); writeInt(primary)
                    })))
                }
            })
        }
        val firstMeta = meta(0)
        val payloadAt = ftyp.size + firstMeta.size + 8
        file.outputStream().buffered().use { out ->
            out.write(ftyp); out.write(meta(payloadAt))
            out.write(data { writeInt(8 + items.sumOf { it.payload.size }); writeBytes("mdat") })
            items.forEach { out.write(it.payload) }
        }
    }

    companion object {
        fun read(file: File): HeifImageContainer {
            require(file.length() in 16..IsoBmff.MAX_BYTES.toLong())
            return read(file.readBytes())
        }
        fun read(bytes: ByteArray): HeifImageContainer {
            val top = IsoBmff.boxes(bytes)
            require(top.first().type == "ftyp" && top.all { it.type in setOf("ftyp", "meta", "mdat", "free", "skip") })
            val ftyp = top.single { it.type == "ftyp" }
            val brands = String(bytes, ftyp.payload, ftyp.end - ftyp.payload, Charsets.ISO_8859_1)
            val avif = brands.chunked(4).any { it == "avif" }
            require(avif || brands.chunked(4).any { it in setOf("heic", "heix", "mif1") })
            val meta = top.single { it.type == "meta" }
            require(IsoBmff.uint(bytes, meta.payload) == 0L)
            val children = IsoBmff.boxes(bytes, meta.payload + 4, meta.end)
            require(children.all { it.type in setOf("hdlr", "pitm", "iloc", "iinf", "iref", "iprp", "idat", "dinf", "grpl") })
            fun reader(box: IsoBmff.Box) = IsoBmff.Reader(bytes, box.payload, box.end)
            val primaryReader = reader(children.single { it.type == "pitm" })
            val pv = primaryReader.u8(); primaryReader.skip(3); require(pv in 0..1)
            val primary = if (pv == 0) primaryReader.u16() else primaryReader.id32()
            val info = children.single { it.type == "iinf" }
            val ir = reader(info); val iv = ir.u8(); ir.skip(3); require(iv in 0..1)
            val count = if (iv == 0) ir.u16() else ir.id32()
            val entries = IsoBmff.boxes(bytes, info.payload + if (iv == 0) 6 else 8, info.end)
            require(count == entries.size && count in 1..4096)
            val itemInfo = entries.map { entry ->
                require(entry.type == "infe")
                val r = reader(entry); val version = r.u8(); val flags = (r.u8() shl 16) or (r.u8() shl 8) or r.u8()
                require(version in 2..3 && flags in 0..1)
                val id = if (version == 2) r.u16() else r.id32()
                require(r.u16() == 0) { "Protected image item" }
                val type = r.fourCC()
                require(type in setOf("hvc1", "av01", "grid", "Exif", "tmap", "mime"))
                Item(id, type, bytes.copyOfRange(entry.end - r.remaining(), entry.end), byteArrayOf(), hidden = flags == 1)
            }
            require(itemInfo.map { it.id }.distinct().size == count && itemInfo.any { it.id == primary })
            val loc = reader(children.single { it.type == "iloc" }); val lv = loc.u8(); loc.skip(3); require(lv in 0..2)
            val sizes = loc.u8(); val sizes2 = loc.u8()
            val offsetSize = sizes ushr 4; val lengthSize = sizes and 15; val baseSize = sizes2 ushr 4
            val indexSize = if (lv == 0) 0 else sizes2 and 15
            val lc = if (lv < 2) loc.u16() else loc.id32(); require(lc == count)
            val payloads = mutableMapOf<Int, ByteArray>()
            var totalBytes = 0L
            repeat(lc) {
                val id = if (lv < 2) loc.u16() else loc.id32()
                val method = if (lv > 0) loc.u16() else 0; require(method in 0..1 && loc.u16() == 0)
                val base = loc.variable(baseSize); val n = loc.u16(); require(n in 1..4096)
                val payload = data {
                    repeat(n) {
                        require(loc.variable(indexSize) == 0L)
                        val offset = loc.variable(offsetSize); val length = loc.variable(lengthSize)
                        require(base <= bytes.size && offset <= bytes.size && length in 1..bytes.size.toLong())
                        val container = if (method == 1) children.single { it.type == "idat" } else null
                        val start = base + offset + (container?.payload ?: 0)
                        val end = start + length
                        require(start >= 0 && end <= bytes.size)
                        require(if (container != null) start >= container.payload && end <= container.end else
                            top.any { it.type == "mdat" && start >= it.payload && end <= it.end })
                        totalBytes += length; require(totalBytes <= IsoBmff.MAX_BYTES)
                        write(bytes, start.toInt(), length.toInt())
                    }
                }
                require(payloads.put(id, payload) == null)
            }
            require(loc.remaining() == 0)
            val iprp = children.single { it.type == "iprp" }
            val iprpChildren = IsoBmff.boxes(bytes, iprp.payload, iprp.end)
            val ipco = iprpChildren.single { it.type == "ipco" }
            val properties = IsoBmff.boxes(bytes, ipco.payload, ipco.end).map { it.raw(bytes) }
            val associations = mutableMapOf<Int, List<Property>>()
            iprpChildren.filter { it.type == "ipma" }.forEach { entry ->
                val r = reader(entry); val version = r.u8(); r.skip(2); val flags = r.u8()
                require(version in 0..1 && flags in 0..1)
                val n = r.id32(); require(n <= 4096)
                repeat(n) {
                    val id = if (version == 0) r.u16() else r.id32(); val p = r.u8()
                    val props = List(p) {
                        val value = if (flags == 0) r.u8() else r.u16()
                        val mask = if (flags == 0) 0x80 else 0x8000
                        Property(value and (mask - 1), value and mask != 0)
                    }.filter { it.index > 0 }
                    require(props.all { it.index <= properties.size } && associations.put(id, props) == null)
                }
                require(r.remaining() == 0)
            }
            val references = children.singleOrNull { it.type == "iref" }?.let { ref ->
                val r = reader(ref); val version = r.u8(); r.skip(3); require(version in 0..1)
                IsoBmff.boxes(bytes, ref.payload + 4, ref.end).map { entry ->
                    val rr = reader(entry); val from = if (version == 0) rr.u16() else rr.id32()
                    val n = rr.u16(); require(n <= 4096)
                    val to = List(n) { if (version == 0) rr.u16() else rr.id32() }
                    require(rr.remaining() == 0)
                    Reference(entry.type, from, to)
                }
            }.orEmpty()
            val ids = itemInfo.map { it.id }.toSet()
            require(payloads.keys == ids && associations.keys.all { it in ids } &&
                references.all { it.from in ids && it.to.all(ids::contains) })
            return HeifImageContainer(avif, primary, itemInfo.map {
                it.copy(payload = payloads.getValue(it.id), properties = associations[it.id].orEmpty())
            }, properties, references)
        }
    }
}
