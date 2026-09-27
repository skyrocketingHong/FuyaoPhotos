package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceSettingsTest {
    @Test fun independentPagesKeepSeparateOwners() {
        val settings = WorkspaceSettings()
        PhotoFeature.entries.forEach { assertEquals(it, settings.owner(it)) }
    }
    @Test fun allSharedPagesUseTheCardsSession() {
        val settings = WorkspaceSettings(sharing = PhotoSharing.ALL)
        PhotoFeature.entries.forEach { assertEquals(PhotoFeature.CARDS, settings.owner(it)) }
    }
    @Test fun metadataAndColorsCanShareWithoutCards() {
        val settings = WorkspaceSettings(sharing = PhotoSharing.PARTIAL,
            sharedFeatures = setOf(PhotoFeature.METADATA, PhotoFeature.COLORS))
        assertEquals(PhotoFeature.CARDS, settings.owner(PhotoFeature.CARDS))
        assertEquals(PhotoFeature.METADATA, settings.owner(PhotoFeature.METADATA))
        assertEquals(PhotoFeature.METADATA, settings.owner(PhotoFeature.COLORS))
    }
    @Test fun incompletePartialSelectionDoesNotMergePhotos() {
        val settings = WorkspaceSettings(sharing = PhotoSharing.PARTIAL, sharedFeatures = setOf(PhotoFeature.COLORS))
        PhotoFeature.entries.forEach { assertEquals(it, settings.owner(it)) }
    }
    @Test fun photoSaveCannotApplyMetadataEditDefaults() {
        val options = ExportOptions(keepExif = false, keepLocation = false, keepCaptureTime = false,
            applePortrait = true, appleStyle = true, appleStyle3 = true, jpegQuality = 82, separateLivePhoto = true).photoSave()
        assertTrue(options.keepExif && options.keepLocation && options.keepCaptureTime)
        assertFalse(options.applePortrait || options.appleStyle || options.appleStyle3)
        assertEquals(82, options.jpegQuality)
        assertTrue(options.separateLivePhoto)
    }
}
