package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.AppleStyleMetadata
import ing.fuyaoskyrocket.photoinfo.domain.metadata.AppleCameraNames
import ing.fuyaoskyrocket.photoinfo.domain.metadata.CaptureMakerNote
import ing.fuyaoskyrocket.photoinfo.domain.metadata.PhotographicStyleReader
import ing.fuyaoskyrocket.photoinfo.domain.model.FieldId
import ing.fuyaoskyrocket.photoinfo.domain.model.PhotoInfo
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class CaptureLabelTest {
    @Test fun physicalLensNamesSurviveDigitalCrops() {
        val tele = AppleCameraNames.resolve("Apple", "iPhone 17 Pro Max", "back triple camera 16.891mm f/2.8", null)!!
        assertEquals("Fusion Telephoto", tele.name)
        assertEquals(4.0, tele.zoomAt(100.0)!!, 0.0)
        assertEquals(8.0, tele.zoomAt(200.0)!!, 0.0)
        val main = AppleCameraNames.resolve("Apple", "iPhone 18 Pro Max", "back triple camera 6.93mm f/1.48", 1)!!
        assertEquals("Fusion Main", main.name)
        assertEquals(2.0, main.zoomAt(48.0)!!, 0.0)
        assertNull(AppleCameraNames.resolve("Apple", "iPhone 14 Pro", "back camera f/2.8", 1))
        assertNull(AppleCameraNames.resolve("Other", "iPhone 18 Pro", "back camera f/1.48", 1))
        assertNull(AppleCameraNames.resolve("Apple", "iPhone 18 Pro", "front camera f/1.9", 6))
        assertNull(AppleCameraNames.resolve("Apple", "iPhone 18 Pro", "unknown", null))
        assertEquals("back camera", AppleCameraNames.displayLensName("iPhone 17 Pro back camera", "iPhone 17 Pro"))
        assertEquals("iPhone 17 Pro Max back camera", AppleCameraNames.displayLensName("iPhone 17 Pro Max back camera", "iPhone 17 Pro"))
    }

    @Test fun styleFieldHidesEmptyValuesAndFollowsIso() {
        val card = PhotoInfo(mapOf(FieldId.ISO to "50"))
        assertEquals(1, card.displayRows().size)
        val styled = card.with(FieldId.PHOTOGRAPHIC_STYLE, "Rose Gold")
        assertEquals("STYLE: ROSE GOLD", styled.displayRows().last().text)
        assertEquals(1, styled.with(FieldId.PHOTOGRAPHIC_STYLE, " ").displayRows().size)
    }

    @Test fun readsBothNativeAndInjectedStylePlistsWithoutFixedOffsets() {
        val note = AppleStyleMetadata.appleNote(null, false, "00112233-4455-6677-8899-aabbccddeeff")
        assertEquals("Standard", CaptureMakerNote.read(note).photographicStyle)
        val native = neutralValues() + mapOf("8" to 1.0, "9" to 1.0, "10" to 0.0, "11" to false, "12" to 1.0)
        assertEquals("Standard", CaptureMakerNote.read(note(native)).photographicStyle)
        assertEquals(1, CaptureMakerNote.read(note(native)).cameraType)
        assertNull(CaptureMakerNote.read(note(native + ("4" to 99.0))).photographicStyle)
        assertNull(CaptureMakerNote.read(note(mapOf("4" to 1.0))).photographicStyle)
    }

    @Test fun corruptNotesAreIgnored() {
        val complete = note(neutralValues())
        for (length in complete.indices) assertNull(CaptureMakerNote.read(complete.copyOf(length)).photographicStyle)
        val bad = complete.copyOf()
        ByteBuffer.wrap(bad).putInt(16 + 12 + 8, Int.MAX_VALUE)
        assertNull(CaptureMakerNote.read(bad).photographicStyle)
    }

    @Test fun legacyPresetIdentifiersMustBeWholeNumbers() {
        assertEquals("Rich Contrast", CaptureMakerNote.read(note(mapOf("_3" to 3.0), tag = 64)).photographicStyle)
        assertNull(CaptureMakerNote.read(note(mapOf("_3" to 3.25), tag = 64)).photographicStyle)
        assertNull(CaptureMakerNote.read(note(mapOf("_3" to 99.0), tag = 64)).photographicStyle)
    }

    @Test fun namedLeicaStyleIsReadButNumericFiltersAreNotGuessed() {
        fun xmp(value: String) = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description xmlns:camera="http://ns.xiaomi.com/photos/1.0/camera/" camera:LeicaStyle="$value"/></rdf:RDF></x:xmpmeta>"""
        assertEquals("Leica Authentic", PhotographicStyleReader.name(CaptureMakerNote.Facts(), xmp("Leica Authentic")))
        assertNull(PhotographicStyleReader.name(CaptureMakerNote.Facts(), xmp("66048")))
        assertNull(PhotographicStyleReader.name(CaptureMakerNote.Facts(), "<!DOCTYPE x [<!ENTITY s SYSTEM 'file:///missing'>]><x>&s;</x>"))
    }

    private fun neutralValues(): Map<String, Any> = mapOf("0" to 1.0, "1" to 0.0, "2" to 0.0, "3" to 1.0,
        "4" to 1.0, "5" to 1.0, "6" to 4.0, "7" to 0.0)

    private fun note(values: Map<String, Any>, tag: Int = 84): ByteArray {
        val writer = AppleStyleMetadata.BplistWriter()
        val plist = writer.finish(writer.addDict(values.map { (key, value) ->
            writer.addStr(key) to if (value is Boolean) writer.addBool(value) else writer.addReal((value as Number).toDouble())
        }))
        val out = ByteBuffer.allocate(44 + plist.size)
        out.put("Apple iOS\u0000\u0000\u0001MM".toByteArray()).putShort(2)
        out.putShort(46).putShort(9).putInt(1).putInt(1)
        out.putShort(tag.toShort()).putShort(7).putInt(plist.size).putInt(44)
        out.putInt(0).put(plist)
        return out.array()
    }
}
