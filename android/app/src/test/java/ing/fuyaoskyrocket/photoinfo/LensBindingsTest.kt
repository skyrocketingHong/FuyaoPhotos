package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.lens.*
import org.junit.Assert.*
import org.junit.Test

class LensBindingsTest {
    private val wide = LensProfile("profile", "Example Phone", "Wide", cameraId="wrong-id", equivalentMin=24.0,
        equivalentMax=24.0, physicalMin=6.75, physicalMax=6.75, exifModel="RAW MODEL", facing="BACK")
    private val hardware = listOf(HardwareLens("wide", "BACK", listOf(6.75), listOf(1.8)),
        HardwareLens("tele", "BACK", listOf(17.0), listOf(2.8)))
    private fun match(profiles: List<LensProfile>, scanned: List<HardwareLens> = hardware) =
        LensBindings.reconcile(profiles, scanned, "local-model", setOf("RAW MODEL"))

    @Test fun missingWrongAndMatchingIdsAllUseFocalEvidence() {
        for (hint in listOf("", "wrong-id", "tele", "wide")) {
            val result = match(listOf(wide.copy(cameraId=hint))).single()
            assertEquals("wide", result.cameraId)
            assertEquals("local-model", result.hardwareDevice)
        }
        assertTrue(match(listOf(wide.copy(physicalMin=8.0,physicalMax=8.0,cameraId="wide"))).single().hardwareDevice.isBlank())
    }

    @Test fun otherDevicesConflictsAndAmbiguousSensorsStayUnbound() {
        assertTrue(match(listOf(wide.copy(device="Other",exifModel="OTHER"))).single().hardwareDevice.isBlank())
        assertTrue(match(listOf(wide.copy(hardwareModel="another-model"))).single().hardwareDevice.isBlank())
        assertTrue(match(listOf(wide), hardware + hardware.first().copy(id="duplicate")).single().hardwareDevice.isBlank())
        assertTrue(match(listOf(wide,wide.copy(id="second"))).all { it.hardwareDevice.isBlank() })
        assertTrue(match(listOf(wide,wide)).all { it.hardwareDevice.isBlank() })
        assertTrue(match(listOf(wide.copy(facing="FRONT"))).single().hardwareDevice.isBlank())
    }

    @Test fun manualLinksRejectMismatchesAndAlreadyUsedSensors() {
        assertThrows(IllegalArgumentException::class.java) { LensBindings.bind(listOf(wide), wide.id, "tele", hardware, "local-model") }
        val linked = LensBindings.bind(listOf(wide), wide.id, "wide", hardware, "local-model").single()
        assertThrows(IllegalArgumentException::class.java) { LensBindings.bind(listOf(linked,wide.copy(id="second")), "second", "wide", hardware, "local-model") }
        assertTrue(match(listOf(linked), emptyList()).single().hardwareDevice.isBlank())
        assertEquals("wide", linked.cameraId)
    }

    @Test fun currentDevicePrecedesAlphabeticalOtherDevices() {
        val groups = LensBindings.groups(listOf(wide.copy(device="Beta",exifModel="B"),wide.copy(device="Zulu"),
            wide.copy(device="alpha",exifModel="A")), "local-model", setOf("RAW MODEL"))
        assertEquals(listOf("Zulu","alpha","Beta"), groups.map { it.device })
        assertEquals(listOf(true,false,false), groups.map { it.isCurrent })
    }

    @Test fun savedEditorStateKeepsHintsAndReadsPreviousVersion() {
        val value = wide.copy(hardwareModel="local-model",stylePrefix="Leica")
        assertEquals(value, LensProfileFields.decode(LensProfileFields.encode(value)))
        val v3 = LensProfileFields.encode(value).dropLast(1).toMutableList().apply { this[0]="lens-v3" }
        assertEquals(value.copy(stylePrefix=""), LensProfileFields.decodeList(v3).single())
        val v2 = LensProfileFields.encode(value).dropLast(2).toMutableList().apply { this[0]="lens-v2" }
        assertEquals(value.copy(hardwareModel="",stylePrefix=""), LensProfileFields.decodeList(v2).single())
    }
}
