package ing.fuyaoskyrocket.photoinfo.platform

/**
 * Bindings to the vendored x265 still encoder (libfuyao_hevc). The library only
 * ships for the 64-bit ABIs; on others [available] is false and callers fall
 * back to the platform encoder.
 */
internal object HevcX265 {
    val available: Boolean by lazy {
        runCatching { System.loadLibrary("fuyao_hevc") }.isSuccess && nativeAvailable()
    }

    @JvmStatic private external fun nativeAvailable(): Boolean

    /** One 8-bit monochrome frame; returns the Annex-B stream (parameter sets + IDR). */
    @JvmStatic external fun nativeEncodeMono(width: Int, height: Int, luma: ByteArray): ByteArray

    /**
     * One 10-bit 4:2:0 frame from a tight P010 buffer (Y plane then interleaved
     * 16-bit left-aligned UV pairs). [colorprim]/[transfer]/[colormatrix] take the
     * container's colr codes and become the stream's VUI.
     */
    @JvmStatic external fun nativeEncodeColor10(width: Int, height: Int, p010: ByteArray, crf: Int,
        colorprim: Int, transfer: Int, colormatrix: Int): ByteArray
}
