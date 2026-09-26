import Foundation

/// Photographic Styles 3 (texture + grain), ported from the device-verified contract of
/// BeetMan/XDRemux-Flutter (Apache-2.0) reverse-engineered from iPhone 18 Pro captures:
/// a `texture_styles` uri metadata item plus twelve per-part 2026 semantic mattes unlock
/// the texture / film grain / glow editor in Apple Photos (iOS 26/27). The native contract
/// requires the plain 2023 styles item to coexist, so this layer never travels alone.
nonisolated enum AppleTextureStyles {
    static let textureStylesContentType = "tag:apple.com,2026:photo:metadata:texture_styles"
    static let matteWidth = 768
    static let matteHeight = 576

    /// The twelve per-part mattes Apple's style editor locates; order matches native captures.
    static let semanticMatteURNS: [String] = [
        "tag:apple.com,2026:photo:aux:semanticnosematte",
        "tag:apple.com,2026:photo:aux:semanticskinmattev2",
        "tag:apple.com,2026:photo:aux:semanticnonfaceskinmatte",
        "tag:apple.com,2026:photo:aux:semanticlipsmatte",
        "tag:apple.com,2026:photo:aux:semanticteethmattev2",
        "tag:apple.com,2026:photo:aux:semanticpersonmatte",
        "tag:apple.com,2026:photo:aux:semanticglassesmattev2",
        "tag:apple.com,2026:photo:aux:semanticeyebrowsmatte",
        "tag:apple.com,2026:photo:aux:semantictattoomatte",
        "tag:apple.com,2026:photo:aux:semantichandsmatte",
        "tag:apple.com,2026:photo:aux:semanticearsmatte",
        "tag:apple.com,2026:photo:aux:semanticfaceskinmatte",
    ]

    /// The Standard textureInfo bplist: NeutrinoCore rejects the item without the
    /// Version/HardwareModel/PortType/CaptureMode/CaptureType fields. The grain seed is
    /// a reproducible per-photo value in Apple's observed range, not a measurement.
    static func textureInfoPayload(grainSeed: Int) -> [UInt8] {
        precondition(grainSeed >= 0 && grainSeed <= 0x7fff_ffff)
        var writer = BplistWriter()
        let kPreset = writer.addStr("Preset"); let vPreset = writer.addStr("Standard")
        let kCaptureType = writer.addStr("CaptureType"); let vCaptureType = writer.addStr("LF")
        let kCaptureMode = writer.addStr("CaptureMode"); let vCaptureMode = writer.addStr("Still")
        let kPortType = writer.addStr("PortType"); let vPortType = writer.addStr("PortTypeBack")
        let kHardware = writer.addStr("HardwareModel"); let vHardware = writer.addStr("iPhone 18 Pro")
        let kPeopleData = writer.addStr("TextureStylePeopleDataVersion"); let vPeopleData = writer.addInt(3)
        let kGrainSeed = writer.addStr("FilmGrainSeed"); let vGrainSeed = writer.addInt(Int64(grainSeed))
        return writer.finish(top: writer.addDict([
            (kPreset, vPreset), (kCaptureType, vCaptureType), (kCaptureMode, vCaptureMode),
            (kPortType, vPortType), (kHardware, vHardware), (kPeopleData, vPeopleData), (kGrainSeed, vGrainSeed)]))
    }

    /// Stable seed from the source identity, mirroring the reference converter's path
    /// hash (Java string hash over UTF-16 code units, masked to 31 bits each step).
    static func grainSeedFor(_ name: String) -> Int {
        var hash = 0
        for unit in name.utf16 { hash = (hash * 31 + Int(unit)) & 0x7fff_ffff }
        return hash
    }
}
