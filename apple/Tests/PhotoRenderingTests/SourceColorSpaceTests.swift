import Testing
@testable import PhotoColorsCore

struct SourceColorSpaceTests {
    @Test func standardProfileNamesSelectTheMatchingSpace() {
        #expect(ColorResultSpace.matchingProfile("sRGB IEC61966-2.1") == .sRGB)
        #expect(ColorResultSpace.matchingProfile("kCGColorSpaceExtendedLinearDisplayP3") == .p3)
        #expect(ColorResultSpace.matchingProfile("Rec. ITU-R BT.2020-1") == .rec2020)
        #expect(ColorResultSpace.matchingProfile("ITU-R BT.2100 PQ") == .rec2020)
        #expect(ColorResultSpace.matchingProfile("Adobe RGB (1998)") == .a98)
        #expect(ColorResultSpace.matchingProfile("Generic Lab Profile") == .cie)
        #expect(ColorResultSpace.matchingProfile("Custom camera profile") == .xyz)
        #expect(ColorResultSpace.matchingProfile("DCI-P3") == .xyz)
    }
}
