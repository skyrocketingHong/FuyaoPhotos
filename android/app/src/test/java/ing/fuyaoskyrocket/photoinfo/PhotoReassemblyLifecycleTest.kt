package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoReassemblyLifecycle
import org.junit.Assert.*
import org.junit.Test

class PhotoReassemblyLifecycleTest {
    private val target = PhotoReassemblyLifecycle.Target(Any(), "bounds", "light-en-normal")

    @Test fun nativeContentNeedsBothPresentedGpuAndDrawnBridge() {
        val lifecycle = PhotoReassemblyLifecycle()
        val request = lifecycle.begin("cards-photo", 0)
        with(request) {
            assertFalse(lifecycle.terminalPresented(generation, feature, target, 1))
            assertFalse(lifecycle.acceptsReady(generation, feature, 1))
            assertTrue(lifecycle.acceptsTarget(generation, feature, target, 2))
            assertTrue(lifecycle.acceptsReady(generation, feature, 3))
            assertFalse(lifecycle.bridgeDrawn(generation, feature, target, 4))
            assertFalse(lifecycle.nativeRecorded(generation, feature, target, 4))
            assertTrue(lifecycle.terminalPresented(generation, feature, target, 5))
            assertFalse(lifecycle.acceptsReady(generation, feature, 6))
            assertFalse(lifecycle.nativeRecorded(generation, feature, target, 6))
            assertEquals(request, lifecycle.current)
            assertTrue(lifecycle.bridgeDrawn(generation, feature, target, 7))
            assertFalse(lifecycle.bridgeDrawn(generation, feature, target, 8))
            assertTrue(lifecycle.nativeRecorded(generation, feature, target, 9))
            assertNull(lifecycle.current)
            assertNull(lifecycle.phase)
        }
    }

    @Test fun changedOwnerGeometryAppearanceOrFeatureCannotAcknowledgeNativeContent() {
        val lifecycle = PhotoReassemblyLifecycle()
        val request = lifecycle.begin("metadata-photo", 0)
        with(request) {
            assertTrue(lifecycle.acceptsTarget(generation, feature, target, 1))
            assertTrue(lifecycle.terminalPresented(generation, feature, target, 2))
            assertTrue(lifecycle.bridgeDrawn(generation, feature, target, 3))
            for (changed in listOf(target.copy(owner = Any()), target.copy(bounds = "resized"),
                target.copy(appearance = "dark-en-normal"), target.copy(appearance = "light-zh-bold"))) {
                assertFalse(lifecycle.nativeRecorded(generation, feature, changed, 4))
            }
            assertFalse(lifecycle.nativeRecorded(generation, "colors-photo", target, 5))
            assertFalse(lifecycle.nativeRecorded(generation + 1, feature, target, 5))
            assertEquals(request, lifecycle.current)
            assertTrue(lifecycle.nativeRecorded(generation, feature, target, 6))
        }
    }

    @Test fun navigationOrImportCancellationInvalidatesCallbacksInEveryPhase() {
        for (phase in PhotoReassemblyLifecycle.Phase.entries) {
            val lifecycle = PhotoReassemblyLifecycle()
            val old = lifecycle.begin("colors-photo", 0)
            if (phase != PhotoReassemblyLifecycle.Phase.WAITING_TARGET)
                assertTrue(lifecycle.acceptsTarget(old.generation, old.feature, target, 1))
            if (phase == PhotoReassemblyLifecycle.Phase.BRIDGE || phase == PhotoReassemblyLifecycle.Phase.NATIVE_READY)
                assertTrue(lifecycle.terminalPresented(old.generation, old.feature, target, 2))
            if (phase == PhotoReassemblyLifecycle.Phase.NATIVE_READY)
                assertTrue(lifecycle.bridgeDrawn(old.generation, old.feature, target, 3))
            lifecycle.cancel()
            assertNull(lifecycle.current)
            assertNull(lifecycle.phase)
            val fresh = lifecycle.begin(old.feature, 10)
            assertFalse(lifecycle.acceptsTarget(old.generation, old.feature, target, 11))
            assertFalse(lifecycle.acceptsReady(old.generation, old.feature, 11))
            assertFalse(lifecycle.terminalPresented(old.generation, old.feature, target, 11))
            assertFalse(lifecycle.bridgeDrawn(old.generation, old.feature, target, 11))
            assertFalse(lifecycle.nativeRecorded(old.generation, old.feature, target, 11))
            assertEquals(fresh, lifecycle.current)
        }
    }

    @Test fun bridgeAndNativeAcknowledgementsCannotOutliveOverallDeadline() {
        val lifecycle = PhotoReassemblyLifecycle()
        val request = lifecycle.begin("metadata-photo", 0)
        with(request) {
            assertTrue(lifecycle.acceptsTarget(generation, feature, target, 1))
            assertFalse(lifecycle.acceptsReady(generation, feature, deadline))
            assertFalse(lifecycle.terminalPresented(generation, feature, target, deadline))
            assertTrue(lifecycle.terminalPresented(generation, feature, target, 2))
            assertFalse(lifecycle.bridgeDrawn(generation, feature, target, deadline))
            assertTrue(lifecycle.bridgeDrawn(generation, feature, target, 3))
            assertFalse(lifecycle.nativeRecorded(generation, feature, target, deadline))
            assertEquals(PhotoReassemblyLifecycle.Expiry.LIFETIME, lifecycle.expiry(deadline))
        }
    }
}
