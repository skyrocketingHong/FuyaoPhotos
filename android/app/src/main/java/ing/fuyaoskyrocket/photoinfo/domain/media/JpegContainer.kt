package ing.fuyaoskyrocket.photoinfo.domain.media

import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded JPEG header parsing; scan data is streamed, never mistaken for metadata. */
object JpegContainer {
    val XMP = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
    private val EXIF = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)
    data class Segment(val marker: Int, val offset: Long, val data: ByteArray)
    data class Layout(val segments: List<Segment>, val scanStart: Long, val primaryEnd: Long, val size: Long)
    data class Part(val offset: Long, val length: Long)
    fun starts(data: ByteArray, prefix: ByteArray) = data.size >= prefix.size && prefix.indices.all { data[it]==prefix[it] }
    fun xmp(layout: Layout): List<String> = layout.segments.filter { it.marker==0xe1 && starts(it.data,XMP) }
        .map { it.data.copyOfRange(XMP.size,it.data.size).toString(Charsets.UTF_8) }
    fun exif(layout: Layout): List<ByteArray> = layout.segments.filter { it.marker==0xe1 && starts(it.data,EXIF) }.map { it.data }
    fun inspect(file: File): Layout {
        RandomAccessFile(file,"r").use { input ->
            require(input.readUnsignedShort()==0xffd8) { "Not a JPEG" }
            val segments=mutableListOf<Segment>()
            while (true) {
                var offset=input.filePointer
                require(input.readUnsignedByte()==0xff) { "Invalid JPEG marker" }
                var marker=input.readUnsignedByte()
                while(marker==0xff) marker=input.readUnsignedByte()
                offset=input.filePointer-2
                require(marker!=0 && marker!=0xd8 && marker !in 0xd0..0xd7)
                if(marker==0xda || marker==0xd9) return Layout(segments,offset,findEnd(file),file.length())
                val size=input.readUnsignedShort()-2
                require(size>=0 && input.filePointer+size<=file.length())
                require(input.filePointer+size<=4L*1024*1024) { "JPEG metadata exceeds limit" }
                val data=ByteArray(size);input.readFully(data);segments+=Segment(marker,offset,data)
            }
        }
    }
    private fun findEnd(file: File): Long {
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            var position=0L
            fun byte(): Int { position++;return input.readUnsignedByte() }
            fun word()=(byte() shl 8) or byte()
            require(word()==0xffd8)
            var scan=false
            while(true) {
                val marker: Int
                if(scan) {
                    var m: Int
                    do {
                        while(byte()!=0xff) { /* entropy bytes */ }
                        do { m=byte() } while(m==0xff)
                    } while(m==0 || m in 0xd0..0xd7)
                    marker=m
                } else {
                    require(byte()==0xff)
                    var m=byte();while(m==0xff)m=byte();marker=m
                }
                if(marker==0xd9)return position
                require(marker!=0xd8 && marker!=0 && marker!=0x01) { "Unsupported JPEG structure" }
                val length=word()-2;require(length>=0)
                repeat(length) { byte() }
                scan=marker==0xda
            }
        }
    }
    private fun mpEntries(data: ByteArray): Pair<ByteBuffer,List<Int>> {
        require(starts(data, byteArrayOf(77,80,70,0)))
        require(data.size>=12)
        val buffer=ByteBuffer.wrap(data).order(when {
            data[4]==73.toByte() && data[5]==73.toByte() -> ByteOrder.LITTLE_ENDIAN
            data[4]==77.toByte() && data[5]==77.toByte() -> ByteOrder.BIG_ENDIAN
            else -> error("Invalid MPF byte order")
        })
        fun uint(at:Int):Long { require(at>=0&&at+4<=data.size);return buffer.getInt(at).toLong() and 0xffffffffL }
        require(buffer.getShort(6).toInt()==42)
        val ifd=uint(8)+4;require(ifd+2<=data.size)
        val count=buffer.getShort(ifd.toInt()).toInt() and 0xffff;require(count<=256)
        for(i in 0 until count) {
            val entry=ifd.toInt()+2+i*12;require(entry+12<=data.size)
            if((buffer.getShort(entry).toInt() and 0xffff)==0xb002) {
                val bytes=uint(entry+4);require(bytes in 16..256 && bytes%16==0L)
                val start=uint(entry+8)+4;require(start+bytes<=data.size)
                return buffer to (0 until (bytes/16).toInt()).map { start.toInt()+it*16 }
            }
        }
        error("MPF image directory missing")
    }
    fun auxiliary(layout: Layout): List<Part> {
        val mpf=layout.segments.filter { it.marker==0xe2 && starts(it.data,byteArrayOf(77,80,70,0)) }
        require(mpf.size<=1)
        return mpf.singleOrNull()?.let { s ->
            val (b,entries)=mpEntries(s.data)
            entries.drop(1).map { entry ->
                Part(s.offset+8+(b.getInt(entry+8).toLong() and 0xffffffffL),b.getInt(entry+4).toLong() and 0xffffffffL)
                    .also { require(it.offset>=layout.primaryEnd && it.length>0 && it.offset+it.length<=layout.size) }
            }
        }.orEmpty()
    }
    /** Rewrites headers and adjusts MPF offsets/sizes. The complete encoded image payload is copied unchanged. */
    fun rewrite(input: File, output: File, exif: List<ByteArray>, xmp: String?) {
        val layout=inspect(input)
        val header=ByteArrayOutputStream();header.write(byteArrayOf(-1,-40))
        fun segment(marker:Int,data:ByteArray) {
            require(data.size<=65533) { "JPEG metadata exceeds APP segment limit" }
            header.write(0xff);header.write(marker);val size=data.size+2;header.write(size ushr 8);header.write(size and 255);header.write(data)
        }
        exif.forEach { segment(0xe1,it) }
        if(xmp!=null)segment(0xe1,XMP+xmp.toByteArray(Charsets.UTF_8))
        val patches=mutableListOf<Pair<Segment,Int>>()
        for(s in layout.segments) {
            if(s.marker==0xe1 && (starts(s.data,EXIF) || starts(s.data,XMP)))continue
            val offset=header.size()
            segment(s.marker,s.data)
            if(s.marker==0xe2 && starts(s.data,byteArrayOf(77,80,70,0)))patches+=s to offset
        }
        val bytes=header.toByteArray();val delta=bytes.size-layout.scanStart
        for((original,offset) in patches) {
            val (buffer,entries)=mpEntries(original.data.copyOf())
            entries.forEachIndexed { index,at ->
                if(index==0) {
                    val size=(buffer.getInt(at+4).toLong() and 0xffffffffL)+delta
                    require(size in 1..0xffffffffL);buffer.putInt(at+4,size.toInt())
                } else {
                    val value=(buffer.getInt(at+8).toLong() and 0xffffffffL)+delta-(offset-original.offset)
                    require(value in 1..0xffffffffL);buffer.putInt(at+8,value.toInt())
                }
            }
            buffer.array().copyInto(bytes,offset+4)
        }
        output.outputStream().use { out ->
            out.write(bytes);copyRange(input,layout.scanStart,layout.size-layout.scanStart,out)
        }
    }
    fun copyRange(file: File, offset: Long, length: Long, output: OutputStream) {
        require(offset>=0 && length>=0 && offset<=file.length()-length)
        RandomAccessFile(file,"r").use { input ->
            input.seek(offset);val buffer=ByteArray(64*1024);var left=length
            while(left>0) { val n=input.read(buffer,0,minOf(left,buffer.size.toLong()).toInt());if(n<0)throw EOFException();output.write(buffer,0,n);left-=n }
        }
    }
}
