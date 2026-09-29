package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.media.StyleSceneSample

/** A deterministic scene for style-layer tests: mid-gray percentiles, uniform light maps. */
internal fun testScene(): StyleSceneSample = StyleSceneSample(
    blackPoint = 0.002, p02 = 0.01, p10 = 0.03, p25 = 0.08, p50 = 0.2, p75 = 0.45, p98 = 0.9, whitePoint = 0.99,
    lightMapC = ByteArray(2048),
    lightMapD = ByteArray(2048))
