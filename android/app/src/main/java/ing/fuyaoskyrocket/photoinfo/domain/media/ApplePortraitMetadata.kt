package ing.fuyaoskyrocket.photoinfo.domain.media

internal object ApplePortraitMetadata {
    const val DISPARITY = "urn:mpeg:hevc:2015:auxid:2"
    const val MATTE = "urn:com:apple:photo:2018:aux:portraiteffectsmatte"

    // Xiaomi's rank plane provides relative disparity, not calibrated distance in metres.
    // Field set follows the device-verified Apple portrait layout: apdi scaling, depthData
    // calibration flags, and the depthBlurEffect/lighting parameters Apple reads on import.
    fun disparityXmp(aperture: Double?): String = """
        <x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:apdi="http://ns.apple.com/pixeldatainfo/1.0/"
        xmlns:depthData="http://ns.apple.com/depthData/1.0/" xmlns:depthBlurEffect="http://ns.apple.com/depthBlurEffect/1.0/"
        xmlns:portraitLightingEffect="http://ns.apple.com/portraitLightingEffect/1.0/">
        <apdi:IntMinValue>0</apdi:IntMinValue><apdi:IntMaxValue>255</apdi:IntMaxValue>
        <apdi:FloatMinValue>0</apdi:FloatMinValue><apdi:FloatMaxValue>1</apdi:FloatMaxValue>
        <apdi:StoredFormat>1278226488</apdi:StoredFormat><apdi:NativeFormat>1751411059</apdi:NativeFormat>
        <apdi:AuxiliaryImageType>disparity</apdi:AuxiliaryImageType>
        <depthData:DepthDataVersion>65541</depthData:DepthDataVersion>
        <depthData:Accuracy>relative</depthData:Accuracy><depthData:Quality>high</depthData:Quality>
        <depthData:Filtered>True</depthData:Filtered>
        ${aperture?.takeIf { it.isFinite() && it in 1.0..64.0 }?.let { "<depthBlurEffect:SimulatedAperture>$it</depthBlurEffect:SimulatedAperture>" }.orEmpty()}
        <portraitLightingEffect:EffectStrength>0.5</portraitLightingEffect:EffectStrength>
        </rdf:Description></rdf:RDF></x:xmpmeta>
    """.trimIndent()

    val matteXmp = """
        <x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:apdi="http://ns.apple.com/pixeldatainfo/1.0/"
        xmlns:portraitEffectsMatte="http://ns.apple.com/portraitEffectsMatte/1.0/">
        <apdi:AuxiliaryImageSubType>portraiteffectsmatte</apdi:AuxiliaryImageSubType>
        <apdi:NativeFormat>1278226488</apdi:NativeFormat><apdi:StoredFormat>1278226488</apdi:StoredFormat>
        <apdi:AuxiliaryImageType>depth</apdi:AuxiliaryImageType>
        <portraitEffectsMatte:PortraitEffectsMatteVersion>65537</portraitEffectsMatte:PortraitEffectsMatteVersion>
        </rdf:Description></rdf:RDF></x:xmpmeta>
    """.trimIndent()
}
