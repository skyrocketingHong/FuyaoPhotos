package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.session.PhotoDraftAccessPolicy
import org.junit.Assert.*
import org.junit.Test

class PhotoDraftAccessPolicyTest {
    @Test fun policyReadsCurrentOwnerBusyStateAndIdentity() {
        val access = PhotoDraftAccessPolicy()
        var busy = false
        var current = "first"
        access.updateSourcePolicy { id, path -> !busy && id == current && path == "/private/$current" }
        assertTrue(access.allows("first", "/private/first"))
        busy = true
        assertFalse(access.allows("first", "/private/first"))
        busy = false
        current = "second"
        assertFalse(access.allows("first", "/private/first"))
        assertFalse(access.allows("second", "/private/first"))
        assertTrue(access.allows("second", "/private/second"))
    }

    @Test fun invalidationRejectsDelayedEditAndSaveCallbacks() {
        val access = PhotoDraftAccessPolicy()
        access.updateSourcePolicy { _, _ -> true }
        val pendingCompletion = { access.allows("discarded", "/private/discarded") }
        access.invalidate(setOf("discarded"))
        assertFalse(pendingCompletion())
        access.updateSourcePolicy { _, _ -> true }
        assertFalse(access.allows("discarded", "/private/discarded"))
        assertTrue(access.allows("kept", "/private/kept"))
    }

    @Test fun unboundPolicyCannotMutateADraft() {
        assertFalse(PhotoDraftAccessPolicy().allows("photo", "/private/photo"))
    }
}
