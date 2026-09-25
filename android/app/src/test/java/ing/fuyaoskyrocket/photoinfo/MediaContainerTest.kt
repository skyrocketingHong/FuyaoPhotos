package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.junit.Assert.*

class MediaContainerTest {
    private fun file(bytes:ByteArray)=File.createTempFile("media-",".jpg").apply { writeBytes(bytes);deleteOnExit() }
    private fun segment(marker:Int,data:ByteArray)=byteArrayOf(-1,marker.toByte(),((data.size+2) ushr 8).toByte(),(data.size+2).toByte())+data
    private fun jpeg(header:ByteArray=byteArrayOf())=byteArrayOf(-1,-40)+header+byteArrayOf(-1,-38,0,2,15,-1,0,23,-1,-48,42,-1,-39)
    private fun box(type:String,data:ByteArray)=ByteBuffer.allocate(8+data.size).putInt(8+data.size).put(type.toByteArray()).put(data).array()
    private val video get()=box("ftyp","isom0000".toByteArray())+box("moov",byteArrayOf())+box("mdat",ByteArray(100) { it.toByte() })
    @Test fun jpegScannerHandlesStuffedBytesAndRestartMarkers() {
        val source=file(jpeg());assertEquals(source.length(),JpegContainer.inspect(source).primaryEnd)
        assertFalse(MotionPhoto.inspect(source,"image/jpeg").blocked)
    }
    @Test fun motionVideoSurvivesHeaderChangesExactly() {
        val plain=file(jpeg());val cover=file(byteArrayOf());val v=video
        JpegContainer.rewrite(plain,cover,emptyList(),MotionPhoto.xmp(JpegContainer.inspect(plain),MotionVideo(0,v.size.toLong(),12345)))
        cover.appendBytes(v)
        val original=MotionPhoto.inspect(cover,"image/jpeg");assertFalse(original.blocked)
        val motion=requireNotNull(original.motion)
        assertEquals(12345L,motion.timestampUs)
        assertArrayEquals(MotionPhoto.digest(cover,motion.offset,motion.length),java.security.MessageDigest.getInstance("SHA-256").digest(v))
        val modified=file(byteArrayOf())
        JpegContainer.rewrite(plain,modified,listOf("Exif\u0000\u0000NEW METADATA".toByteArray()),MotionPhoto.xmp(JpegContainer.inspect(plain),motion))
        java.io.FileOutputStream(modified,true).use { JpegContainer.copyRange(cover,motion.offset,motion.length,it) }
        val final=requireNotNull(MotionPhoto.inspect(modified,"image/jpeg").motion)
        assertArrayEquals(MotionPhoto.digest(cover,motion.offset,motion.length),MotionPhoto.digest(modified,final.offset,final.length))
    }
    @Test fun unknownTailsAndTruncatedVideosAreBlocked() {
        assertTrue(MotionPhoto.inspect(file(jpeg()+video),"image/jpeg").blocked)
        assertTrue(MotionPhoto.inspect(file(jpeg()+byteArrayOf(1,2,3)),"image/jpeg").blocked)
        val plain=file(jpeg());val out=file(byteArrayOf());val v=video
        JpegContainer.rewrite(plain,out,emptyList(),MotionPhoto.xmp(JpegContainer.inspect(plain),MotionVideo(0,v.size.toLong())))
        out.appendBytes(v.dropLast(1).toByteArray())
        assertTrue(MotionPhoto.inspect(out,"image/jpeg").blocked)
    }
    @Test fun sdrXiaomiPortraitTailWithoutGainMapIsAccepted() {
        val bokeh="http://ns.xiaomi.com/photos/1.0/camera/bokeh"
        val camera="http://ns.xiaomi.com/photos/1.0/camera/"
        val unblurred=jpeg()
        val depth=ByteArray(2048) { it.toByte() }
        val tail=unblurred+"MCBOKEHSOT".toByteArray(Charsets.US_ASCII)+depth
        val xmp=JpegContainer.XMP+"""
            <x:xmpmeta xmlns:x="adobe:ns:meta/" xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:RDF xmlns:bokeh="$bokeh" xmlns:camera="$camera"><rdf:Description bokeh:capsInfo="1" bokeh:capsStream="1">
            <camera:XMPMeta>&lt;d rawlength="${unblurred.size}" depthlength="${tail.size-unblurred.size}"/&gt;</camera:XMPMeta>
            </rdf:Description></rdf:RDF></x:xmpmeta>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val photo=file(jpeg(segment(0xe1,xmp))+tail)
        val envelope=MotionPhoto.inspect(photo,"image/jpeg")
        assertFalse(envelope.blocked)
        assertFalse(envelope.hdrHint)
        val part=requireNotNull(envelope.portraitTail)
        assertEquals(tail.size.toLong(),part.length)
        assertEquals(photo.length()-tail.size,part.offset)
        // SDR exports re-encode without a gain map, so the merged packet starts empty.
        val merged=MotionPhoto.xiaomiPortraitXmp(JpegContainer.inspect(photo),null)
        assertTrue(merged.contains("capsInfo"))
        // The embedded declaration survives as an escaped attribute or element value.
        assertTrue(Regex("rawlength=[\"']?&quot;?${unblurred.size}").containsMatchIn(merged))
    }
    @Test fun gainMapDeclaredOnlyInsideTheAuxiliaryImageIsAccepted() {
        // Writers without a primary gain-map directory declare hdrgm in the map JPEG itself.
        val mapHeader=segment(0xe1,JpegContainer.XMP+
            """<x:xmpmeta xmlns:x="adobe:ns:meta/" xmlns:h="http://ns.adobe.com/hdr-gain-map/1.0/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description h:Version="2.0"/></rdf:RDF></x:xmpmeta>""".toByteArray())
        val map=jpeg(mapHeader)
        val data=ByteArray(4+8+2+12+4+32)
        val b=ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        b.put(byteArrayOf(77,80,70,0)); b.put(byteArrayOf(77,77)); b.putShort(42); b.putInt(8)
        b.putShort(1); b.putShort(0xb002.toShort()); b.putShort(7); b.putInt(32); b.putInt(26); b.putInt(0)
        // Size is measured first, the directory entries are patched in, then the file is assembled.
        val primarySize=jpeg(segment(0xe2,data)).size
        b.putInt(30+4,primarySize); b.putInt(30+8,0)
        b.putInt(46+4,map.size); b.putInt(46+8,primarySize-10)
        val envelope=MotionPhoto.inspect(file(jpeg(segment(0xe2,data))+map),"image/jpeg")
        assertFalse("reason=${envelope.blockReason}",envelope.blocked)
        assertTrue(envelope.hdrHint)
        // An auxiliary JPEG that declares no gain map still fails closed.
        val plainMap=jpeg()
        data.fill(0); val b2=ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        b2.put(byteArrayOf(77,80,70,0)); b2.put(byteArrayOf(77,77)); b2.putShort(42); b2.putInt(8)
        b2.putShort(1); b2.putShort(0xb002.toShort()); b2.putShort(7); b2.putInt(32); b2.putInt(26); b2.putInt(0)
        b2.putInt(30+4,primarySize); b2.putInt(30+8,0)
        b2.putInt(46+4,plainMap.size); b2.putInt(46+8,primarySize-10)
        val blocked=MotionPhoto.inspect(file(jpeg(segment(0xe2,data))+plainMap),"image/jpeg")
        assertTrue(blocked.blocked)
        assertEquals("Unrecognized auxiliary image data",blocked.blockReason)
    }
    @Test fun motionXiaomiPortraitKeepsTailBeforeVideo() {
        val bokeh="http://ns.xiaomi.com/photos/1.0/camera/bokeh"
        val camera="http://ns.xiaomi.com/photos/1.0/camera/"
        val container="http://ns.google.com/photos/1.0/container/"
        val item="http://ns.google.com/photos/1.0/container/item/"
        val rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
        val cam="http://ns.google.com/photos/1.0/camera/"
        val unblurred=jpeg()
        val depth=ByteArray(2048) { it.toByte() }
        val tail=unblurred+"MCBOKEHSOT".toByteArray(Charsets.US_ASCII)+depth
        val v=video
        val xmp=JpegContainer.XMP+"""
            <x:xmpmeta xmlns:x="adobe:ns:meta/" xmlns:rdf="$rdf">
            <rdf:RDF xmlns:bokeh="$bokeh" xmlns:camera="$camera" xmlns:Camera="$cam">
            <rdf:Description Camera:MotionPhoto="1" Camera:MotionPhotoVersion="1" Camera:MotionPhotoPresentationTimestampUs="77"
            bokeh:capsInfo="1" bokeh:capsStream="1">
            <camera:XMPMeta>&lt;d rawlength="${unblurred.size}" depthlength="${tail.size-unblurred.size}"/&gt;</camera:XMPMeta>
            <Container:Directory xmlns:Container="$container"><rdf:Seq>
            <rdf:li rdf:parseType="Resource"><Container:Item xmlns:Item="$item" Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/></rdf:li>
            <rdf:li rdf:parseType="Resource"><Container:Item xmlns:Item="$item" Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="${v.size}"/></rdf:li>
            </rdf:Seq></Container:Directory>
            </rdf:Description></rdf:RDF></x:xmpmeta>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val photo=file(jpeg(segment(0xe1,xmp))+tail+v)
        val envelope=MotionPhoto.inspect(photo,"image/jpeg")
        assertFalse(envelope.blocked)
        val motion=requireNotNull(envelope.motion)
        assertEquals(v.size.toLong(),motion.length)
        assertEquals(photo.length()-v.size,motion.offset)
        val part=requireNotNull(envelope.portraitTail)
        assertEquals(tail.size.toLong(),part.length)
        assertEquals(photo.length()-v.size-tail.size,part.offset)
    }
    @Test fun maliciousXmpCannotResolveExternalEntities() {
        val xml="<!DOCTYPE x [<!ENTITY leak SYSTEM 'file:///etc/passwd'>]><x>&leak;</x>"
        assertTrue(MotionPhoto.inspect(file(jpeg(segment(0xe1,JpegContainer.XMP+xml.toByteArray()))),"image/jpeg").blocked)
    }
    @Test fun existingXmpDirectoriesAreReplacedOnceWithoutLosingHdrMetadata() {
        val container = "http://ns.google.com/photos/1.0/container/"
        val rdf = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
        val hdr = "http://ns.adobe.com/hdr-gain-map/1.0/"
        for (count in listOf(0, 1, 3)) {
            val old = (0 until count).joinToString("") {
                "<c:Directory><rdf:Seq><rdf:li>obsolete-$it</rdf:li></rdf:Seq></c:Directory>"
            }
            val xml = """<x:xmpmeta xmlns:x="adobe:ns:meta/" xmlns:rdf="$rdf" xmlns:c="$container"
                xmlns:h="$hdr" xmlns:other="urn:unrelated"><rdf:RDF>
                <rdf:Description h:Version="1.0" h:GainMapMax="2.0">$old<other:Directory>keep</other:Directory></rdf:Description>
                </rdf:RDF></x:xmpmeta>"""
            val source = file(jpeg(segment(0xe1, JpegContainer.XMP + xml.toByteArray())))
            val rebuilt = requireNotNull(MotionPhoto.xmp(JpegContainer.inspect(source), MotionVideo(0, video.size.toLong(), 1234)))
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val doc = factory.newDocumentBuilder().parse(rebuilt.byteInputStream())
            assertEquals(1, doc.getElementsByTagNameNS(container, "Directory").length)
            assertEquals(1, doc.getElementsByTagNameNS("urn:unrelated", "Directory").length)
            val description = doc.getElementsByTagNameNS(rdf, "Description").item(0) as org.w3c.dom.Element
            assertEquals("2.0", description.getAttributeNS(hdr, "GainMapMax"))
            assertFalse(rebuilt.contains("obsolete-"))
            val result = file(byteArrayOf())
            JpegContainer.rewrite(source, result, emptyList(), rebuilt)
            result.appendBytes(video)
            val envelope = MotionPhoto.inspect(result, "image/jpeg")
            assertFalse(envelope.blocked)
            assertEquals(1234L, requireNotNull(envelope.motion).timestampUs)
        }
    }
    @Test fun mpfOffsetsRemainValidWithHeaderInsertionAndRemoval() {
        for(order in listOf(ByteOrder.LITTLE_ENDIAN,ByteOrder.BIG_ENDIAN)) for(xmpBefore in listOf(true,false)) {
            val map=jpeg()
            val data=ByteArray(4+8+2+12+4+32)
            val b=ByteBuffer.wrap(data).order(order)
            b.put(byteArrayOf(77,80,70,0));b.put(if(order==ByteOrder.LITTLE_ENDIAN)byteArrayOf(73,73) else byteArrayOf(77,77))
            b.putShort(42);b.putInt(8);b.putShort(1);b.putShort(0xb002.toShort());b.putShort(7);b.putInt(32);b.putInt(26);b.putInt(0)
            val oldXmp=segment(0xe1,JpegContainer.XMP+"<x/>".toByteArray())
            val primary=jpeg(if(xmpBefore) oldXmp+segment(0xe2,data) else segment(0xe2,data)+oldXmp)
            val mpfOffset=2+if(xmpBefore)oldXmp.size else 0
            b.putInt(30+4,primary.size);b.putInt(30+8,0)
            b.putInt(46+4,map.size);b.putInt(46+8,primary.size-(mpfOffset+8))
            val source=file(jpeg(if(xmpBefore) oldXmp+segment(0xe2,data) else segment(0xe2,data)+oldXmp)+map)
            val result=file(byteArrayOf())
            JpegContainer.rewrite(source,result,listOf("Exif\u0000\u0000INSERTED METADATA".toByteArray()),null)
            val layout=JpegContainer.inspect(result);val aux=JpegContainer.auxiliary(layout).single()
            assertEquals(layout.primaryEnd,aux.offset);assertEquals(map.size.toLong(),aux.length)
            assertArrayEquals(MotionPhoto.digest(source,source.length()-map.size,map.size.toLong()),MotionPhoto.digest(result,aux.offset,aux.length))
            val combined=file(byteArrayOf());val v=video
            JpegContainer.rewrite(result,combined,emptyList(),MotionPhoto.xmp(layout,MotionVideo(0,v.size.toLong(),101)))
            combined.appendBytes(v)
            val envelope=MotionPhoto.inspect(combined,"image/jpeg")
            assertFalse(envelope.blocked);assertTrue(envelope.hdrHint)
            val videoPart=requireNotNull(envelope.motion)
            assertEquals(101L,videoPart.timestampUs)
            assertArrayEquals(java.security.MessageDigest.getInstance("SHA-256").digest(v),MotionPhoto.digest(combined,videoPart.offset,videoPart.length))
            val gain=JpegContainer.auxiliary(JpegContainer.inspect(combined)).single()
            assertArrayEquals(MotionPhoto.digest(result,aux.offset,aux.length),MotionPhoto.digest(combined,gain.offset,gain.length))
            // A decoded/re-encoded HDR cover already contains a Container:Directory.
            // Rebuild that directory again and verify both MPF and the appended video.
            val rewritten=file(byteArrayOf())
            JpegContainer.rewrite(combined,rewritten,emptyList(),MotionPhoto.xmp(JpegContainer.inspect(combined),videoPart))
            val repeated=MotionPhoto.inspect(rewritten,"image/jpeg")
            assertFalse(repeated.blocked)
            val repeatedVideo=requireNotNull(repeated.motion)
            assertEquals(videoPart.timestampUs,repeatedVideo.timestampUs)
            assertArrayEquals(MotionPhoto.digest(combined,videoPart.offset,videoPart.length),MotionPhoto.digest(rewritten,repeatedVideo.offset,repeatedVideo.length))
            val repeatedGain=JpegContainer.auxiliary(JpegContainer.inspect(rewritten)).single()
            assertArrayEquals(MotionPhoto.digest(combined,gain.offset,gain.length),MotionPhoto.digest(rewritten,repeatedGain.offset,repeatedGain.length))
        }
    }
    @Test fun gainmapNeutralValueUsesMetadataInsteadOfFixedGray() {
        assertEquals(0.0,GainmapMath.neutral(.5,1.0,4.0,1.0,0.0,0.0),1e-8)
        assertEquals(.5,GainmapMath.neutral(.5,.5,2.0,1.0,0.0,0.0),1e-8)
        assertEquals(kotlin.math.sqrt(.5),GainmapMath.neutral(.5,.5,2.0,2.0,0.0,0.0),1e-8)
    }
    @Test fun primaryPaddingAndElementFormMetadataAreSupported() {
        val v=video
        val xml="""<x xmlns:c="http://ns.google.com/photos/1.0/camera/" xmlns:i="http://ns.google.com/photos/1.0/container/item/">
            <c:MotionPhoto>1</c:MotionPhoto><item><i:Semantic>Primary</i:Semantic><i:Mime>image/jpeg</i:Mime><i:Padding>4</i:Padding></item>
            <item><i:Semantic>MotionPhoto</i:Semantic><i:Mime>video/mp4</i:Mime><i:Length>${v.size}</i:Length></item></x>"""
        val input=file(jpeg(segment(0xe1,JpegContainer.XMP+xml.toByteArray()))+ByteArray(4)+v)
        assertFalse(MotionPhoto.inspect(input,"image/jpeg").blocked)
    }
    @Test fun unsupportedContainersFailClosed() {
        assertTrue(MotionPhoto.inspect(file(byteArrayOf()),"image/heic").blocked)
        assertTrue(MotionPhoto.inspect(file(byteArrayOf()),"image/avif").blocked)
        assertTrue(MotionPhoto.inspect(file(byteArrayOf()),"image/gif").blocked)
    }
    @Test fun oldMicrovideoOffsetIsReadWithoutDependingOnPrefix() {
        val v=video
        val xml="<x xmlns:c='http://ns.google.com/photos/1.0/camera/' c:MicroVideo='1' c:MicroVideoOffset='${v.size}' c:MicroVideoPresentationTimestampUs='100'/>"
        val source=file(jpeg(segment(0xe1,JpegContainer.XMP+xml.toByteArray()))+v)
        val motion=requireNotNull(MotionPhoto.inspect(source,"image/jpeg").motion)
        assertEquals(v.size.toLong(),motion.length);assertEquals(100L,motion.timestampUs)
    }
}
