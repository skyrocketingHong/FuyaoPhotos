package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.session.PhotoDraftStorage
import ing.fuyaoskyrocket.photoinfo.domain.session.PhotoSessionKind
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoDraftStorageTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun metadataReplacementNeverRemovesCardDrafts() {
        val cards = PhotoDraftStorage(temporary.root, PhotoSessionKind.CARDS)
        val metadata = PhotoDraftStorage(temporary.root, PhotoSessionKind.METADATA)
        val card = cards.newFile().apply { writeText("card edits") }
        val previous = metadata.newFile().apply { writeText("old metadata photo") }
        val selected = metadata.newFile().apply { writeText("new metadata photo") }
        metadata.removeOthers(listOf(selected))
        assertFalse(previous.exists())
        assertEquals("card edits", card.readText())
        assertEquals("new metadata photo", metadata.restoreFile(selected.path).readText())
    }

    @Test fun closingCardsNeverRemovesMetadataSelection() {
        val cards = PhotoDraftStorage(temporary.root, PhotoSessionKind.CARDS)
        val metadata = PhotoDraftStorage(temporary.root, PhotoSessionKind.METADATA)
        val card = cards.newFile().apply { writeText("card") }
        val selected = metadata.newFile().apply { writeText("metadata") }
        cards.removeOthers(emptyList())
        assertFalse(card.exists())
        assertEquals("metadata", metadata.restoreFile(selected.path).readText())
    }

    @Test fun restorationRejectsAnotherSessionDirectory() {
        val cards = PhotoDraftStorage(temporary.root, PhotoSessionKind.CARDS)
        val metadata = PhotoDraftStorage(temporary.root, PhotoSessionKind.METADATA)
        val file = cards.newFile().apply { writeText("card") }
        assertThrows(IllegalArgumentException::class.java) { metadata.restoreFile(file.path) }
        assertEquals(file.canonicalFile, cards.restoreFile(file.path))
    }
}
