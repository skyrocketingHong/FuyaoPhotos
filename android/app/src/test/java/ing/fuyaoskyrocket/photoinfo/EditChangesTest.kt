package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.model.*
import ing.fuyaoskyrocket.photoinfo.domain.session.*
import org.junit.Assert.*
import org.junit.Test

class EditChangesTest {
    @Test fun unchangedRestoredAndRevertedFormsDoNotWarn() {
        val initial = listOf("Device", "Lens", "23.0", "")
        assertFalse(EditChanges.form(initial, initial, setOf(2, 3)))
        assertFalse(EditChanges.form(initial, listOf("Device ", "Lens", "23", ""), setOf(2, 3)))
        assertTrue(EditChanges.form(initial, listOf("Device", "Changed", "23", ""), setOf(2, 3)))
        assertTrue(EditChanges.form(initial, listOf("Device", "Lens", "23", "invalid"), setOf(2, 3)))
        assertFalse(EditChanges.form(initial, initial, setOf(2, 3)))
    }

    @Test fun automaticLocationsDoNotBecomeUserEditsButManualEditsDo() {
        val base = PhotoEditSnapshot("one.photo", emptyMap(), CardStyle())
        fun stamp(value: PhotoEditSnapshot) = EditChanges.fingerprint(value, 100, true, "default")
        val automaticallyLocated = base.copy(overrides=mapOf(FieldId.LOCATION to "Hangzhou"), resolvedLocation="Hangzhou")
        assertEquals(stamp(base), stamp(automaticallyLocated))
        assertEquals(stamp(base), stamp(automaticallyLocated.copy(locationEdited=true)))
        assertNotEquals(stamp(base), stamp(automaticallyLocated.copy(overrides=emptyMap(), locationEdited=true)))
        assertNotEquals(stamp(base), stamp(base.copy(overrides=mapOf(FieldId.AUTHOR to "New author"))))
        assertNotEquals(stamp(base), stamp(base.copy(style=CardStyle(textScale=1.5f))))
        assertEquals(stamp(base), stamp(base.copy(style=CardStyle())))
    }

    @Test fun qualityMetadataAndFontChangesAreTrackedAndSuccessfulSaveBecomesBaseline() {
        val edit = PhotoEditSnapshot("photo", mapOf(FieldId.ISO to "800"), CardStyle())
        val initial = EditChanges.fingerprint(edit, 100, true, "font-a")
        assertNotEquals(initial, EditChanges.fingerprint(edit, 80, true, "font-a"))
        assertNotEquals(initial, EditChanges.fingerprint(edit, 100, false, "font-a"))
        assertNotEquals(initial, EditChanges.fingerprint(edit, 100, true, "font-b"))
        val savedBaseline = EditChanges.fingerprint(edit, 80, false, "font-b")
        val restored = PhotoEditSnapshot.restore(edit.fields())
        assertEquals(savedBaseline, EditChanges.fingerprint(restored, 80, false, "font-b"))
    }
}
