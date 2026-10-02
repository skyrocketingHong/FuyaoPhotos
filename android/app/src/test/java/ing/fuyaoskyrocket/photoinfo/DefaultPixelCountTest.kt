package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.model.*
import ing.fuyaoskyrocket.photoinfo.domain.session.PhotoEditSnapshot
import org.junit.Assert.*
import org.junit.Test

class DefaultPixelCountTest {
    private val source = PhotoInfo(mapOf(FieldId.IMAGE_SIZE to "5.9MP", FieldId.CAMERA to "Matched telephoto", FieldId.ISO to "100"))

    @Test fun defaultPreferenceKeepsActualPhotoPixels() {
        assertFalse(EditorSettings().preferLensPixelCount)
        assertEquals("5.9MP", EditorSettings().imageSizeFor(source[FieldId.IMAGE_SIZE], 200.0))
    }

    @Test fun officialPixelsAreUsedOnlyWhenEnabledAndAvailable() {
        val settings = EditorSettings(preferLensPixelCount = true)
        val card = settings.initialCardInfo(source, 200.0)
        assertEquals("200MP", card[FieldId.IMAGE_SIZE])
        assertEquals("5.9MP", source[FieldId.IMAGE_SIZE])
        assertEquals(source[FieldId.CAMERA], card[FieldId.CAMERA])
        assertEquals(source[FieldId.ISO], card[FieldId.ISO])
        for (missing in listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 1001.0)) {
            assertEquals("5.9MP", settings.imageSizeFor(source[FieldId.IMAGE_SIZE], missing))
        }
    }

    @Test fun savedCardValuesSurviveAChangedDefaultPreference() {
        val card = EditorSettings(preferLensPixelCount = true).initialCardInfo(source, 200.0)
        val snapshot = PhotoEditSnapshot.capture("/private/source.jpg", source, card, CardStyle(), "", false)
        val restored = PhotoEditSnapshot.restore(snapshot.fields()).info(source)
        assertEquals("200MP", restored[FieldId.IMAGE_SIZE])
        assertEquals("5.9MP", EditorSettings().initialCardInfo(source, 200.0)[FieldId.IMAGE_SIZE])
    }
}
