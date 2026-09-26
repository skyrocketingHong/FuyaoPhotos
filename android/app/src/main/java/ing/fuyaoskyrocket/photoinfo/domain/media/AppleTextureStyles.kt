package ing.fuyaoskyrocket.photoinfo.domain.media

/**
 * Photographic Styles 3 (texture + grain), ported from the device-verified contract of
 * BeetMan/XDRemux-Flutter (Apache-2.0) reverse-engineered from iPhone 18 Pro captures:
 * a `texture_styles` uri metadata item plus twelve per-part 2026 semantic mattes unlock
 * the texture / film grain / glow editor in Apple Photos (iOS 26/27). The native contract
 * requires the plain 2023 styles item to coexist, so this layer never travels alone.
 */
internal object AppleTextureStyles {
    const val TEXTURE_STYLES_CONTENT_TYPE = "tag:apple.com,2026:photo:metadata:texture_styles"
    const val MATTE_WIDTH = 768
    const val MATTE_HEIGHT = 576

    /** The twelve per-part mattes Apple's style editor locates; order matches native captures. */
    val SEMANTIC_MATTE_URNS = listOf(
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
    )

    /**
     * The Standard textureInfo bplist: NeutrinoCore rejects the item without the
     * Version/HardwareModel/PortType/CaptureMode/CaptureType fields. The grain seed is
     * a reproducible per-photo value in Apple's observed range, not a measurement.
     */
    fun textureInfoPayload(grainSeed: Long): ByteArray {
        require(grainSeed in 0..0x7fffffffL) { "grain seed range" }
        val writer = AppleStyleMetadata.BplistWriter()
        val top = writer.run {
            val kPreset = addStr("Preset"); val vPreset = addStr("Standard")
            val kCaptureType = addStr("CaptureType"); val vCaptureType = addStr("LF")
            val kCaptureMode = addStr("CaptureMode"); val vCaptureMode = addStr("Still")
            val kPortType = addStr("PortType"); val vPortType = addStr("PortTypeBack")
            val kHardware = addStr("HardwareModel"); val vHardware = addStr("iPhone 18 Pro")
            val kPeopleData = addStr("TextureStylePeopleDataVersion"); val vPeopleData = addInt(3)
            val kGrainSeed = addStr("FilmGrainSeed"); val vGrainSeed = addInt(grainSeed)
            addDict(listOf(
                kPreset to vPreset,
                kCaptureType to vCaptureType,
                kCaptureMode to vCaptureMode,
                kPortType to vPortType,
                kHardware to vHardware,
                kPeopleData to vPeopleData,
                kGrainSeed to vGrainSeed))
        }
        return writer.finish(top)
    }

    /** Stable seed from the source identity, mirroring the reference converter's path hash. */
    fun grainSeedFor(name: String): Long =
        name.codeUnits().fold(0L) { hash, unit -> (hash * 31 + unit) and 0x7fffffffL }

    private fun String.codeUnits(): IntArray = IntArray(length) { this[it].code }
}
