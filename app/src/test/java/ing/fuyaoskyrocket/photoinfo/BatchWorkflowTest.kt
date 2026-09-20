package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.model.*
import ing.fuyaoskyrocket.photoinfo.domain.session.PhotoEditSnapshot
import ing.fuyaoskyrocket.photoinfo.presentation.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class BatchWorkflowTest {
    @Test fun perPhotoEditsRoundTripWithoutBorrowingAnotherPhotosMetadata() {
        val bases = listOf(PhotoInfo(mapOf(FieldId.ISO to "100", FieldId.AUTHOR to "Camera A")),
            PhotoInfo(mapOf(FieldId.ISO to "800", FieldId.AUTHOR to "Camera B")))
        val snapshots = listOf(
            PhotoEditSnapshot.capture("one.photo", bases[0], bases[0].with(FieldId.AUTHOR, "").with(FieldId.LOCATION, "Paris"),
                CardStyle(textScale = 1.5f), "London", true),
            PhotoEditSnapshot.capture("two.photo", bases[1], bases[1], CardStyle(opacity = .3f), "", false))
        val saved = snapshots.flatMap { it.fields() }
        val restored = saved.chunked(PhotoEditSnapshot.FIELD_COUNT).map(PhotoEditSnapshot::restore)
        assertEquals("", restored[0].info(bases[0])[FieldId.AUTHOR])
        assertEquals("Paris", restored[0].info(bases[0])[FieldId.LOCATION])
        assertEquals("London", restored[0].resolvedLocation)
        assertTrue(restored[0].locationEdited)
        assertEquals("800", restored[1].info(bases[1])[FieldId.ISO])
        assertEquals("Camera B", restored[1].info(bases[1])[FieldId.AUTHOR])
        assertEquals(1.5f, restored[0].style.textScale, .001f)
        assertEquals(1f, restored[1].style.textScale, .001f)
    }

    @Test fun uneditedLargeExifValuesStayOutOfSavedStateWithoutTruncation() {
        val base = PhotoInfo(mapOf(FieldId.AUTHOR to "Name ".repeat(20_000)))
        val snapshot = PhotoEditSnapshot.capture("one.photo", base, base, CardStyle(), "", false)
        val fields = snapshot.fields()
        assertTrue(fields.sumOf { it.length } < 1024)
        assertEquals(base, PhotoEditSnapshot.restore(fields).info(base))
    }

    @Test fun exportsRunInSelectionOrderWithAtMostOneItemActive() = runBlocking {
        var active = false
        val visited = mutableListOf<Int>()
        val progress = mutableListOf<Int>()
        val result = runBatch(listOf("a", "b", "c"), { it.message.orEmpty() }, { progress += it.completed }) { index, item ->
            assertFalse(active); active = true
            yield(); visited += index
            active = false
            item.uppercase()
        }
        assertEquals(listOf(0, 1, 2), visited)
        assertEquals(listOf("A", "B", "C"), result.saved)
        assertEquals(listOf(0, 1, 2, 3), progress)
        assertTrue(result.failures.isEmpty())
    }

    @Test fun oneFailurePreservesSuccessfulResultsAndContinuesToTheNextPhoto() = runBlocking {
        val result = runBatch(listOf(1, 2, 3), { "invalid input" }, {}) { _, item ->
            if (item == 2) throw IllegalArgumentException()
            "saved-$item"
        }
        assertEquals(listOf("saved-1", "saved-3"), result.saved)
        assertEquals(1, result.failures.single().index)
        assertEquals("invalid input", result.failures.single().message)
    }

    @Test fun cancellationNeverStartsRemainingExports() = runBlocking {
        val visited = mutableListOf<Int>()
        try {
            runBatch(listOf(0, 1, 2), { it.message.orEmpty() }, {}) { _, item ->
                visited += item
                if (item == 1) throw CancellationException("closing session")
                item
            }
            fail("Cancellation must reach the session owner")
        } catch (_: CancellationException) {
            assertEquals(listOf(0, 1), visited)
        }
    }
}
