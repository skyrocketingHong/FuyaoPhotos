package ing.fuyaoskyrocket.photoinfo.domain.media

internal object ApplePortraitMetadata {
    const val DISPARITY = "urn:mpeg:hevc:2015:auxid:2"
    const val MATTE = "urn:com:apple:photo:2018:aux:portraiteffectsmatte"

    /**
     * Calibration inputs for the disparity sidecar. Every value derives from the photo's
     * own metadata or is a neutral identity — nothing is invented beyond the declared
     * "relative" accuracy: the focal model comes from the EXIF 35 mm equivalent field
     * and the pixel size from the crop factor it implies.
     */
    data class Calibration(
        val mainWidth: Int,
        val mainHeight: Int,
        val auxWidth: Int,
        val auxHeight: Int,
        val focalLength35mm: Double?,
        val focalLengthMm: Double?,
        val headroomStops: Double,
    ) {
        private val longSide: Int get() = maxOf(mainWidth, mainHeight)
        /** Focal length in main-image pixels implied by the 35 mm equivalent field. */
        val focalPixels: Double? get() = focalLength35mm?.takeIf { it > 0 }?.let { longSide * it / 36.0 }
        /** Sensor pixel pitch in mm implied by the EXIF crop factor. */
        val pixelSizeMm: Double? get() {
            val f35 = focalLength35mm?.takeIf { it > 0 } ?: return null
            val f = focalLengthMm?.takeIf { it > 0 } ?: return null
            val sensorWidth = 36.0 / (f35 / f)
            return sensorWidth / longSide
        }
        val intrinsicScale: Double get() = if (mainWidth > 0) auxWidth.toDouble() / mainWidth else 1.0
    }

    // Xiaomi's rank plane provides relative disparity, not calibrated distance in metres.
    // The field set follows the device-verified Apple portrait layout: apdi scaling, the
    // full depthData calibration block, and the depthBlurEffect rendering parameters whose
    // REND record is what unlocks depth editing in Apple Photos.
    fun disparityXmp(aperture: Double?, calibration: Calibration): String {
        val fx = calibration.focalPixels?.let { it * calibration.intrinsicScale }
        val cx = calibration.auxWidth / 2.0
        val cy = calibration.auxHeight / 2.0
        val distortion = List(8) { 0.0 }
        fun seq(values: List<Double>) = values.joinToString("") { "<rdf:li>$it</rdf:li>" }
        val calibrationBlock = if (fx != null && fx > 0) """
        <depthData:IntrinsicMatrixReferenceWidth>${calibration.auxWidth}</depthData:IntrinsicMatrixReferenceWidth>
        <depthData:IntrinsicMatrixReferenceHeight>${calibration.auxHeight}</depthData:IntrinsicMatrixReferenceHeight>
        <depthData:IntrinsicMatrix><rdf:Seq>${seq(listOf(fx, 0.0, 0.0, 0.0, fx, 0.0, cx, cy, 1.0))}</rdf:Seq></depthData:IntrinsicMatrix>
        <depthData:InverseLensDistortionCoefficients><rdf:Seq>${seq(distortion)}</rdf:Seq></depthData:InverseLensDistortionCoefficients>
        <depthData:LensDistortionCoefficients><rdf:Seq>${seq(distortion)}</rdf:Seq></depthData:LensDistortionCoefficients>
        <depthData:LensDistortionCenterOffsetX>$cx</depthData:LensDistortionCenterOffsetX>
        <depthData:LensDistortionCenterOffsetY>$cy</depthData:LensDistortionCenterOffsetY>
        <depthData:PixelSize>${calibration.pixelSizeMm ?: 0.0}</depthData:PixelSize>
        <depthData:ExtrinsicMatrix><rdf:Seq>${seq(listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0))}</rdf:Seq></depthData:ExtrinsicMatrix>
        """.trimIndent() else ""
        val activation = AppleDepthRendering.activation(focusNormalized = 0.5,
            headroomStops = calibration.headroomStops)
        val rend = java.util.Base64.getEncoder().encodeToString(
            AppleDepthRendering.renderingParameters(activation, calibration.headroomStops))
        val simulatedAperture = aperture?.takeIf { it.isFinite() && it in 1.0..64.0 }
        return """
            <x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="XMP Core 6.0.0">
               <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                  <rdf:Description rdf:about=""
                        xmlns:apdi="http://ns.apple.com/pixeldatainfo/1.0/"
                        xmlns:depthData="http://ns.apple.com/depthData/1.0/"
                        xmlns:depthBlurEffect="http://ns.apple.com/depthBlurEffect/1.0/"
                        xmlns:portraitLightingEffect="http://ns.apple.com/portraitLightingEffect/2.0/">
                     <apdi:IntMaxValue>255</apdi:IntMaxValue>
                     <apdi:StoredFormat>1278226488</apdi:StoredFormat>
                     <apdi:NativeFormat>1751411059</apdi:NativeFormat>
                     <apdi:IntMinValue>0</apdi:IntMinValue>
                     <apdi:FloatMaxValue>1</apdi:FloatMaxValue>
                     <apdi:FloatMinValue>0</apdi:FloatMinValue>
                     <apdi:AuxiliaryImageType>disparity</apdi:AuxiliaryImageType>
                     <depthData:DepthDataVersion>65541</depthData:DepthDataVersion>
                     <depthData:Quality>high</depthData:Quality>$calibrationBlock
                     <depthData:Accuracy>relative</depthData:Accuracy>
                     <depthData:Filtered>True</depthData:Filtered>
                     <depthBlurEffect:RenderingParameters>$rend</depthBlurEffect:RenderingParameters>${simulatedAperture?.let { "<depthBlurEffect:SimulatedAperture>$it</depthBlurEffect:SimulatedAperture>" }.orEmpty()}
                     <portraitLightingEffect:EffectStrength>0.5</portraitLightingEffect:EffectStrength>
                  </rdf:Description>
               </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent() + "\n"
    }

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
