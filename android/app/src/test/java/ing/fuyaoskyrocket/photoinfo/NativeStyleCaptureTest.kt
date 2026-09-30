package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.HeifGraph
import ing.fuyaoskyrocket.photoinfo.domain.media.IsoBmff
import ing.fuyaoskyrocket.photoinfo.domain.media.VideoMetadata
import ing.fuyaoskyrocket.photoinfo.domain.metadata.CaptureMakerNote
import ing.fuyaoskyrocket.photoinfo.domain.model.ExportOptions
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File

class NativeStyleCaptureTest {
    @Test fun nativeMakerNoteReadsCameraAndStyle() {
        val directory = System.getenv("FUYAO_STYLE_SAMPLE_DIR")
        assumeNotNull(directory)
        val graph = HeifGraph.inspect(File(directory!!, "IMG_0311.HEIC"))
        assertTrue(graph.hasStyleMetadata && graph.hasTextureStyles)
        assertEquals("Standard", graph.texturePreset)
        val note = CaptureMakerNote.fromExif(graph.exifPayload)
        assertNotNull(note)
        val capture = CaptureMakerNote.read(note)
        assertEquals(1, capture.cameraType)
        assertEquals("Standard", capture.photographicStyle)
    }

    @Test fun nativeMovieKeepsStyleTracksWhileClearingLocationAndTime() {
        val directory = System.getenv("FUYAO_STYLE_SAMPLE_DIR")
        assumeNotNull(directory)
        val source = File(directory!!, "IMG_0311.MOV")
        verifyMoviePreservation(source)
    }

    @Test fun legacyMovieKeepsDeltaMapWhileClearingLocationAndTime() {
        val source = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .take(6).map { File(it, "docs/samples/apple-airdrop-full-IMG_8565/IMG_8565.MOV") }
            .firstOrNull { it.isFile }
        assumeNotNull(source)
        verifyMoviePreservation(source!!)
    }

    private fun verifyMoviePreservation(source: File) {
        val output = File.createTempFile("native-style-output", ".mov")
        try {
            VideoMetadata.copy(source, 0, source.length(), output,
                ExportOptions(keepExif = true, keepLocation = false, keepCaptureTime = false))
            val original = source.readBytes()
            val rewritten = output.readBytes()
            assertEquals(original.size, rewritten.size)
            fun protectedBoxes(bytes: ByteArray): List<ByteArray> {
                val result = mutableListOf<ByteArray>()
                fun walk(start: Int, end: Int) {
                    for (box in IsoBmff.boxes(bytes, start, end)) {
                        if (box.type in setOf("mdat", "stsd", "tref", "tagc")) result += bytes.copyOfRange(box.start, box.end)
                        else if (box.type in setOf("moov", "trak", "mdia", "minf", "stbl", "udta")) walk(box.payload, box.end)
                    }
                }
                walk(0, bytes.size)
                return result
            }
            val before = protectedBoxes(original)
            val after = protectedBoxes(rewritten)
            assertEquals(before.size, after.size)
            before.zip(after).forEach { (a, b) -> assertArrayEquals(a, b) }
            assertTrue(before.size > 20)
        } finally { output.delete() }
    }
}
