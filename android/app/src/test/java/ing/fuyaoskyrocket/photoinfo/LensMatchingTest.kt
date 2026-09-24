package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.lens.*
import ing.fuyaoskyrocket.photoinfo.domain.metadata.AndroidLensMetadata
import org.junit.Assert.*
import org.junit.Test

class LensMatchingTest {
    private val wide = LensProfile("wide", "Product name", "WIDE", "0", 14.0, 14.0, .6, .6,
        exifModel="raw-model", hardwareDevice="phone-a", facing="BACK")
    private val main = wide.copy(id="main", name="MAIN", cameraId="1", equivalentMin=23.0, equivalentMax=23.0, zoomMin=1.0, zoomMax=1.0)
    private val tele = wide.copy(id="tele", name="TELE", cameraId="2", equivalentMin=75.0, equivalentMax=100.0, zoomMin=3.2, zoomMax=4.3)
    private val lenses get() = listOf(wide, main, tele)
    private fun resolve(mm: Double, profiles: List<LensProfile> = lenses, physical: Double = 0.0) =
        AndroidLensMetadata.resolve("Brand", "raw-model", "", mm, profiles=profiles, physicalMm=physical)

    @Test fun fixedMainLensMatchesDigitalCropsWithoutClampingZoomToOne() {
        assertEquals("46 MM (2X)", resolve(46.0).focalLength)
        for (mm in listOf(71.0, 71.3, 72.0)) {
            assertEquals("MAIN", resolve(mm).camera)
            assertTrue(resolve(mm).focalLength.endsWith("(3.1X)"))
        }
        assertEquals("TELE", resolve(75.0).camera)
        assertEquals("75 MM (3.2X)", resolve(75.0).focalLength)
    }
    @Test fun ultrawideCropRangeStopsAtTheMainLens() {
        assertEquals("14 MM (0.6X)", resolve(14.0).focalLength)
        assertEquals("WIDE", resolve(21.0).camera)
        assertEquals("21 MM (0.9X)", resolve(21.0).focalLength)
        assertEquals("23 MM (1X)", resolve(23.0).focalLength)
        assertEquals("MAIN", resolve(23.0).camera)
    }
    @Test fun explicitDigitalLimitWorksWithoutACompleteDeviceProfileSet() {
        val configured = listOf(main.copy(digitalZoomMax=3.1))
        assertEquals("MAIN", resolve(71.3, configured).camera)
        assertEquals("", resolve(74.0, configured).camera)
        assertEquals("", resolve(46.0, listOf(main)).camera) // No unbounded guesses from a lone native point.
        assertFalse(main.copy(digitalZoomMax=.5).valid())
    }
    @Test fun telephotoDigitalZoomExtrapolatesFromItsNativeEndpoint() {
        assertEquals("100 MM (4.3X)", resolve(100.0).focalLength)
        assertEquals("", resolve(200.0).camera)
        val result = resolve(200.0, lenses.dropLast(1) + tele.copy(digitalZoomMax=10.0))
        assertEquals("TELE", result.camera)
        assertEquals("200 MM (8.6X)", result.focalLength)
    }
    @Test fun physicalIdentityOutranksFocalOnlySwitchingAndConfiguredCropLimits() {
        // Synthetic physical values test provenance; they are not a device specification.
        val known = listOf(main.copy(physicalMin=8.7, physicalMax=8.7, digitalZoomMax=3.1),
            tele.copy(physicalMin=17.0, physicalMax=22.7))
        assertEquals("MAIN", resolve(75.0, known, 8.7).camera)
        assertEquals("TELE", resolve(75.0, known, 17.0).camera)
        assertEquals("", resolve(75.0, known, 4.0).camera)
    }
    @Test fun overlappingCoverageDoesNotAssignALensWithoutEvidence() {
        val overlap = listOf(main.copy(digitalZoomMax=6.0), tele)
        assertEquals("", resolve(75.0, overlap).camera)
        assertEquals("75 MM", resolve(75.0, overlap).focalLength)
    }
    @Test fun aFrontCameraDoesNotTruncateRearCropCoverage() {
        val front = wide.copy(id="front", cameraId="3", name="FRONT", equivalentMin=21.0, equivalentMax=21.0, zoomMin=1.0, zoomMax=1.0, facing="FRONT")
        assertEquals("WIDE", resolve(22.0, lenses + front).camera)
    }
    @Test fun anExplicitFrontPhotoDoesNotUseUnknownRearProfileCropRules() {
        val legacy=lenses.map { it.copy(facing="") }
        val result=AndroidLensMetadata.resolve("Brand","raw-model","Front camera",21.0,profiles=legacy)
        assertEquals("Front camera",result.camera)
        assertEquals("21 MM",result.focalLength)
    }
    @Test fun productNamesAreIndependentFromExifIdentity() {
        val renamed = lenses.map { it.copy(device="小米 17 Ultra") }
        assertEquals("小米 17 Ultra", resolve(46.0, renamed).deviceName)
        assertEquals("MAIN", resolve(46.0, renamed).camera)
        assertEquals("", AndroidLensMetadata.resolve("", "小米 17 Ultra", "", 46.0, profiles=renamed).camera)
        assertEquals("", AndroidLensMetadata.resolve("raw-model", "", "", 46.0, profiles=renamed).camera)
    }
    @Test fun legacyFixedLensZoomCoverageMigratesWithoutDroppingTheProfile() {
        val legacy = LensProfile("old", "raw-model", "MAIN", "1", 23.0, 23.0, 1.0, 3.1)
        val migrated = legacy.upgradeLegacy("phone-a")
        assertTrue(migrated.valid())
        assertEquals("old", migrated.id)
        assertEquals("raw-model", migrated.exifModel)
        assertEquals(1.0, migrated.zoomMax!!, .001)
        assertEquals(3.1, migrated.digitalZoomMax!!, .001)
        assertEquals("MAIN", resolve(71.3, listOf(migrated)).camera)
        assertTrue(migrated.boundTo("phone-a", "1"))
    }
    @Test fun saveableProfileFieldsPreserveNewAndOldDrafts() {
        val modern = main.copy(device="Friendly", digitalZoomMax=3.1)
        assertEquals(modern, LensProfileFields.decode(LensProfileFields.encode(modern)))
        val old = listOf("old", "raw-model", "MAIN", "1", "23", "23", "1", "3.1", "", "")
        val decoded = LensProfileFields.decodeList(old).single()
        assertEquals("raw-model", decoded.exifModel)
        assertEquals(3.1, decoded.digitalZoomMax!!, .001)
    }
    @Test fun scansSeparateConfiguredIdsByHardwareNamespaceAndHandleDeleteUndo() {
        val other = main.copy(id="other", hardwareDevice="phone-b")
        assertEquals(listOf("1", "2"), LensBindings.unconfigured(listOf("0", "1", "2"), listOf(wide, other), "phone-a"))
        assertTrue(LensBindings.unconfigured(listOf("0", "1", "2"), lenses, "phone-a").isEmpty())
        assertEquals(listOf("1"), LensBindings.unconfigured(listOf("0", "1", "2"), listOf(wide, tele), "phone-a"))
        assertTrue(LensBindings.hasDuplicates(listOf(main, main.copy(id="duplicate"))))
        assertFalse(LensBindings.hasDuplicates(listOf(main, other)))
    }
}
