package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.features.colors.domain.color.SourceColorSpace
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceColorSpaceTest {
    @Test fun standardProfilesChooseTheirColorSpace() {
        assertEquals(SourceColorSpace.SRGB, SourceColorSpace.fromProfile("sRGB IEC61966-2.1"))
        assertEquals(SourceColorSpace.DISPLAY_P3, SourceColorSpace.fromProfile("Display P3"))
        assertEquals(SourceColorSpace.REC2020, SourceColorSpace.fromProfile("Rec. ITU-R BT.2020-1"))
        assertEquals(SourceColorSpace.REC2020, SourceColorSpace.fromProfile("ITU-R BT.2100 HLG"))
        assertEquals(SourceColorSpace.A98, SourceColorSpace.fromProfile("Adobe RGB (1998)"))
        assertEquals(SourceColorSpace.LAB, SourceColorSpace.fromProfile("Generic Lab Profile"))
    }
    @Test fun unknownProfilesDoNotPretendToBeSRGB() {
        assertEquals(SourceColorSpace.XYZ, SourceColorSpace.fromProfile("Custom camera profile"))
        assertEquals(SourceColorSpace.XYZ, SourceColorSpace.fromProfile("DCI-P3"))
        assertEquals(SourceColorSpace.XYZ, SourceColorSpace.fromProfile(null))
    }
}
