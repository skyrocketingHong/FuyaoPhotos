package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoDepartureEffects
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoDepartureEffectsTest {
    @Test fun retainedImportReachesTheReplacementUi() {
        val relay = PhotoDepartureEffects()
        val events = mutableListOf<String>()
        val oldOwner = Any()
        val newOwner = Any()
        relay.bind(oldOwner) { events += "old:$it" }
        val importCompletion = { relay.dispatch("colors-photo") }
        relay.bind(newOwner) { events += "new:$it" }
        relay.unbind(oldOwner)
        importCompletion()
        assertEquals(listOf("new:colors-photo"), events)
    }

    @Test fun backgroundCompletionIsNotReplayedAgainstAnotherPhoto() {
        val relay = PhotoDepartureEffects()
        val events = mutableListOf<String>()
        val owner = Any()
        relay.bind(owner) { events += it }
        relay.unbind(owner)
        relay.dispatch("old-photo")
        relay.bind(Any()) { events += it }
        assertEquals(emptyList<String>(), events)
    }
}
