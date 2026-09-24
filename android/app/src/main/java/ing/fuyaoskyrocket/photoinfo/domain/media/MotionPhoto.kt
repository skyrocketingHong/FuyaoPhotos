package ing.fuyaoskyrocket.photoinfo.domain.media

import java.io.*
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element

data class MotionVideo(val offset: Long, val length: Long, val timestampUs: Long = -1, val mime: String = "video/mp4")
data class MediaEnvelope(val jpeg: Boolean, val hdrHint: Boolean = false, val motion: MotionVideo? = null,
    val portraitTail: JpegContainer.Part? = null, val blocked: Boolean = false,
    val bitDepth: Int = 8, val hdrTransfer: Boolean = false, val hdrTransferCode: Int = 0)

/** Only recognized, structurally valid containers may be rewritten. Unknown auxiliary data fails closed. */
object MotionPhoto {
    private const val CAMERA="http://ns.google.com/photos/1.0/camera/"
    private const val CONTAINER="http://ns.google.com/photos/1.0/container/"
    private const val ITEM="http://ns.google.com/photos/1.0/container/item/"
    private const val RDF="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    private const val XIAOMI_BOKEH="http://ns.xiaomi.com/photos/1.0/camera/bokeh"
    private const val XIAOMI_CAMERA="http://ns.xiaomi.com/photos/1.0/camera/"
    private fun document(xml:String):org.w3c.dom.Document {
        require(xml.length<=128*1024 && !xml.contains("<!DOCTYPE",true) && !xml.contains("<!ENTITY",true)) { "Unsafe XMP" }
        val factory=DocumentBuilderFactory.newInstance();factory.isNamespaceAware=true
        // ASVS 1.5: bounded XMP, no DTD/entity declarations, no external resolution.
        factory.isExpandEntityReferences=false
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities",false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false) }
        val builder=factory.newDocumentBuilder()
        builder.setEntityResolver { _,_ -> org.xml.sax.InputSource(StringReader("")) }
        return builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }
    private fun value(element:Element,ns:String,key:String):String? {
        if(element.hasAttributeNS(ns,key))return element.getAttributeNS(ns,key)
        for(i in 0 until element.childNodes.length) {
            val child=element.childNodes.item(i)
            if(child is Element && child.namespaceURI==ns && child.localName==key)return child.textContent
        }
        return null
    }

    fun inspectHeif(file: File, embeddedXmp: String?, payload: HeifGraph.MotionPayload): MotionVideo {
        require(payload.headerBytes == 8L && payload.length > 0 &&
            payload.offset > payload.headerBytes && payload.offset + payload.length == file.length()) {
            "Invalid HEIC motion box"
        }
        val doc = document(requireNotNull(embeddedXmp) { "HEIC motion XMP is missing" })
        val elements = doc.getElementsByTagName("*")
        fun attr(ns: String, key: String): String? = (0 until elements.length).firstNotNullOfOrNull {
            value(elements.item(it) as Element, ns, key)
        }
        require(attr(CAMERA, "MotionPhoto") == "1" && attr(CAMERA, "MotionPhotoVersion") in listOf(null, "1")) {
            "HEIC motion declaration is invalid"
        }
        val items = (0 until elements.length).map { elements.item(it) as Element }
            .filter { value(it, ITEM, "Semantic") != null }
        require(items.size == 2 && value(items[0], ITEM, "Semantic") == "Primary" &&
            value(items[1], ITEM, "Semantic") == "MotionPhoto") { "HEIC motion directory is invalid" }
        require(value(items[0], ITEM, "Mime") in listOf("image/heic", "image/heif") &&
            value(items[0], ITEM, "Length") in listOf(null, "0") &&
            value(items[0], ITEM, "Padding")?.toLongOrNull() == payload.headerBytes) {
            "HEIC primary directory entry is invalid"
        }
        val videoMime = value(items[1], ITEM, "Mime")
        require(videoMime in listOf("video/mp4", "video/quicktime") &&
            value(items[1], ITEM, "Length")?.toLongOrNull() == payload.length &&
            value(items[1], ITEM, "Padding") == null) { "HEIC video directory entry is invalid" }
        val timestamp = attr(CAMERA, "MotionPhotoPresentationTimestampUs")?.toLongOrNull() ?: -1L
        require(timestamp >= -1L) { "HEIC motion timestamp is invalid" }
        validateVideo(file, payload.offset, payload.length)
        return MotionVideo(payload.offset, payload.length, timestamp, requireNotNull(videoMime))
    }
    fun inspect(file:File,mime:String,embeddedXmp:String?=null):MediaEnvelope {
        if(mime!="image/jpeg") {
            // HEIC/AVIF and animated/high-bit-depth formats require a separate preservation encoder.
            return MediaEnvelope(false,blocked=mime!="image/png" || !plainPng(file))
        }
        return try {
            val layout=JpegContainer.inspect(file)
            val packets=JpegContainer.xmp(layout).ifEmpty { listOfNotNull(embeddedXmp) }
            val hdr=layout.segments.any { s ->
                val raw=s.data.toString(Charsets.ISO_8859_1)
                raw.contains("hdr-gain-map",true) || raw.contains("gainmap",true) || raw.contains("21496")
            }
            var video:MotionVideo?=null
            var declared=false
            var primaryPadding=0L
            var xiaomiPortrait=false
            var portraitLengths:Pair<Long,Long>?=null
            for(packet in packets) {
                val doc=document(packet);val elements=doc.getElementsByTagName("*")
                fun attr(ns:String,key:String):String?=(0 until elements.length).firstNotNullOfOrNull { value(elements.item(it) as Element,ns,key) }
                if (!attr(XIAOMI_BOKEH,"capsInfo").isNullOrEmpty() &&
                    !attr(XIAOMI_BOKEH,"capsStream").isNullOrEmpty()) {
                    xiaomiPortrait=true
                    val depthXml=attr(XIAOMI_CAMERA,"XMPMeta").orEmpty()
                    fun declaredLength(name:String):Long? = Regex("\\b$name=\"([0-9]{1,10})\"")
                        .find(depthXml)?.groupValues?.get(1)?.toLongOrNull()
                    val raw=declaredLength("rawlength")
                    val depth=declaredLength("depthlength")
                    if(raw!=null && depth!=null) portraitLengths=raw to depth
                }
                val modern=attr(CAMERA,"MotionPhoto")
                if(modern!=null && modern!="1")continue
                val enabled=modern=="1" || attr(CAMERA,"MicroVideo")=="1"
                if(!enabled)continue
                declared=true
                require(attr(CAMERA,"MotionPhotoVersion") in listOf(null,"1"))
                val items=(0 until elements.length).map { elements.item(it) as Element }.filter { value(it,ITEM,"Semantic")!=null }
                val motionItems=items.filter { value(it,ITEM,"Semantic")=="MotionPhoto" }
                val length:Long
                var videoMime="video/mp4"
                if(motionItems.isNotEmpty()) {
                    require(motionItems.size==1 && items.last()===motionItems.single())
                    require(items.firstOrNull()?.let { value(it,ITEM,"Semantic") }=="Primary")
                    require(items.all { value(it,ITEM,"Semantic") in listOf("Primary","GainMap","MotionPhoto") })
                    primaryPadding=value(items.first(),ITEM,"Padding")?.toLongOrNull() ?: 0L
                    require(primaryPadding in 0..1024*1024)
                    length=requireNotNull(value(motionItems.single(),ITEM,"Length")?.toLongOrNull())
                    videoMime=value(motionItems.single(),ITEM,"Mime").orEmpty()
                    require(videoMime in listOf("video/mp4","video/quicktime"))
                } else length=requireNotNull(attr(CAMERA,"MicroVideoOffset")?.toLongOrNull())
                require(length>0 && length<file.length())
                val offset=file.length()-length
                require(offset>=layout.primaryEnd)
                validateVideo(file,offset,length)
                val timestamp=(attr(CAMERA,"MotionPhotoPresentationTimestampUs") ?: attr(CAMERA,"MicroVideoPresentationTimestampUs"))?.toLongOrNull() ?: -1
                require(timestamp>=-1)
                require(video==null) { "Multiple motion declarations" }
                video=MotionVideo(offset,length,timestamp,videoMime)
            }
            require(!declared || video!=null)
            val auxiliary=JpegContainer.auxiliary(layout)
            require(auxiliary.size<=1 && (auxiliary.isEmpty() || hdr))
            val end=auxiliary.singleOrNull()?.let { it.offset+it.length } ?: layout.primaryEnd
            val portraitTail=if(video==null && xiaomiPortrait && hdr && end<file.length()) {
                inspectXiaomiPortraitTail(file,end,requireNotNull(portraitLengths))
            } else null
            require(end+(if(auxiliary.isEmpty() && video!=null)primaryPadding else 0L)==
                (portraitTail?.offset ?: video?.offset ?: file.length())) { "Unrecognized trailing photo data" }
            MediaEnvelope(true,hdr,video,portraitTail)
        } catch(_:Exception) { MediaEnvelope(true,blocked=true) }
    }

    private fun inspectXiaomiPortraitTail(file:File,offset:Long,lengths:Pair<Long,Long>):JpegContainer.Part {
        val length=file.length()-offset
        require(length in 1_024L..(64L*1024*1024)) { "Xiaomi portrait tail exceeds limit" }
        require(lengths.first>0 && lengths.second>0 && lengths.first+lengths.second==length)
        return JpegContainer.Part(offset,length).also { part ->
            val found=XiaomiPortraitTail.layout(XiaomiPortraitTail.read(file,part))
            require(found.secondEnd.toLong()==lengths.first)
        }
    }
    private fun plainPng(file:File):Boolean = runCatching {
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            val signature=ByteArray(8);input.readFully(signature)
            require(signature.contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10)))
            var consumed=8L
            while(consumed<file.length()) {
                val length=input.readInt();require(length>=0 && length.toLong()+12<=file.length()-consumed)
                val kind=ByteArray(4);input.readFully(kind);val type=kind.toString(Charsets.US_ASCII)
                require(type !in listOf("acTL","cICP"))
                if(type=="IHDR") { require(length==13); val d=ByteArray(13);input.readFully(d);require(d[8].toInt()<=8) }
                else { var left=length;while(left>0) { val n=input.skipBytes(left);require(n>0);left-=n } }
                input.readInt();consumed+=length+12L
                if(type=="IEND")return@use consumed==file.length()
            }
            false
        }
    }.getOrDefault(false)
    fun validateVideo(file:File,offset:Long,length:Long) {
        RandomAccessFile(file,"r").use { input ->
            input.seek(offset);var remaining=length;var first=true;var media=false;var movie=false
            while(remaining>0) {
                require(remaining>=8);var size=input.readInt().toLong() and 0xffffffffL
                val bytes=ByteArray(4);input.readFully(bytes);val type=bytes.toString(Charsets.US_ASCII)
                var header=8L
                if(size==1L) { require(remaining>=16);size=input.readLong();header=16 }
                if(size==0L)size=remaining
                require(size>=header && size<=remaining)
                if(first) { require(type=="ftyp" && size>=16);first=false }
                if(type=="mdat")media=true
                if(type=="moov")movie=true
                input.seek(input.filePointer+size-header);remaining-=size
            }
            require(!first && media && movie) { "Incomplete motion video" }
        }
    }
    fun xmp(encoded:JpegContainer.Layout,video:MotionVideo?):String? {
        val packets=JpegContainer.xmp(encoded);require(packets.size<=1)
        if(video==null)return packets.singleOrNull()
        require(video.length>0 && video.timestampUs>=-1 && video.mime in listOf("video/mp4","video/quicktime"))
        val doc=document(packets.singleOrNull() ?: "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"$RDF\"><rdf:Description/></rdf:RDF></x:xmpmeta>")
        val descriptions=doc.getElementsByTagNameNS(RDF,"Description");require(descriptions.length>0)
        val description=descriptions.item(0) as Element
        for((key,v) in listOf("MotionPhoto" to "1","MotionPhotoVersion" to "1","MotionPhotoPresentationTimestampUs" to video.timestampUs.toString())) description.setAttributeNS(CAMERA,"Camera:$key",v)
        val old=doc.getElementsByTagNameNS(CONTAINER,"Directory")
        // Android's Harmony DOM returns a snapshot here, whereas desktop DOMs
        // can return a live list. Capture nodes before mutation and remove each once.
        val oldDirectories=List(old.length) { old.item(it) }
        oldDirectories.forEach { node ->
            requireNotNull(node.parentNode) { "XMP directory has no parent" }.removeChild(node)
        }
        val directory=doc.createElementNS(CONTAINER,"Container:Directory");val seq=doc.createElementNS(RDF,"rdf:Seq");directory.appendChild(seq);description.appendChild(directory)
        val gainmap=JpegContainer.auxiliary(encoded).singleOrNull()
        fun item(mime:String,semantic:String,length:Long,padding:Long=0) {
            val li=doc.createElementNS(RDF,"rdf:li");li.setAttributeNS(RDF,"rdf:parseType","Resource")
            val element=doc.createElementNS(CONTAINER,"Container:Item")
            element.setAttributeNS(ITEM,"Item:Mime",mime);element.setAttributeNS(ITEM,"Item:Semantic",semantic);element.setAttributeNS(ITEM,"Item:Length",length.toString())
            if(padding>0)element.setAttributeNS(ITEM,"Item:Padding",padding.toString())
            li.appendChild(element);seq.appendChild(li)
        }
        item("image/jpeg","Primary",0,gainmap?.let { it.offset-encoded.primaryEnd } ?: 0)
        if(gainmap!=null)item("image/jpeg","GainMap",gainmap.length)
        item(video.mime,"MotionPhoto",video.length)
        val out=StringWriter();TransformerFactory.newInstance().newTransformer().transform(DOMSource(doc),StreamResult(out));return out.toString()
    }
    fun xiaomiPortraitXmp(original:JpegContainer.Layout, encoded:String?):String {
        val packet=JpegContainer.xmp(original).singleOrNull()
            ?: throw IOException("Xiaomi portrait XMP is missing")
        val source=document(packet)
        val target=document(requireNotNull(encoded) { "Encoded HDR XMP is missing" })
        val sourceDescriptions=source.getElementsByTagNameNS(RDF,"Description")
        val targetDescriptions=target.getElementsByTagNameNS(RDF,"Description")
        require(sourceDescriptions.length>0 && targetDescriptions.length>0)
        for(description in 0 until targetDescriptions.length) {
            val existing=targetDescriptions.item(description) as Element
            val vendorAttributes=(0 until existing.attributes.length).map { existing.attributes.item(it) }
                .filter { it.namespaceURI==XIAOMI_BOKEH || it.namespaceURI==XIAOMI_CAMERA }
            vendorAttributes.forEach { existing.removeAttributeNS(it.namespaceURI,it.localName) }
        }
        val into=targetDescriptions.item(0) as Element
        var copied=0
        for(description in 0 until sourceDescriptions.length) {
            val from=sourceDescriptions.item(description) as Element
            for(index in 0 until from.attributes.length) {
                val attribute=from.attributes.item(index)
                if(attribute.namespaceURI==XIAOMI_BOKEH || attribute.namespaceURI==XIAOMI_CAMERA) {
                    into.setAttributeNS(attribute.namespaceURI,attribute.nodeName,attribute.nodeValue)
                    copied++
                }
            }
        }
        require(copied>=2) { "Xiaomi portrait attributes are missing" }
        val output=StringWriter()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(target),StreamResult(output))
        return output.toString()
    }
    fun digest(file:File,offset:Long,length:Long):ByteArray {
        val digest=MessageDigest.getInstance("SHA-256")
        JpegContainer.copyRange(file,offset,length,object:OutputStream() {
            override fun write(b:Int) { digest.update(b.toByte()) }
            override fun write(b:ByteArray,off:Int,len:Int) { digest.update(b,off,len) }
        });return digest.digest()
    }
}
