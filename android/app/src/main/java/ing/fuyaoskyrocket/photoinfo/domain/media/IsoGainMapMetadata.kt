package ing.fuyaoskyrocket.photoinfo.domain.media

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log2
import kotlin.math.roundToLong

/** Numeric ISO 21496-1 gain-map metadata. Container assembly is a separate step. */
data class IsoGainMapMetadata(
    val gainMinLog2: List<Float>,
    val gainMaxLog2: List<Float>,
    val gamma: List<Float>,
    val offsetSdr: List<Float>,
    val offsetHdr: List<Float>,
    val capacityMinLog2: Float,
    val capacityMaxLog2: Float,
) {
    val channelCount: Int = gainMinLog2.size

    init {
        require(channelCount == 1 || channelCount == 3)
        require(listOf(gainMaxLog2, gamma, offsetSdr, offsetHdr).all { it.size == channelCount })
        require(listOf(gainMinLog2, gainMaxLog2, gamma, offsetSdr, offsetHdr)
            .flatMap { it }.all(Float::isFinite))
        require((0 until channelCount).all { index ->
            gainMaxLog2[index] >= gainMinLog2[index] && gamma[index] > 0 &&
                offsetSdr[index] >= 0 && offsetHdr[index] >= 0
        })
        require(capacityMinLog2.isFinite() && capacityMaxLog2.isFinite() &&
            capacityMinLog2 >= 0 && capacityMaxLog2 >= capacityMinLog2)
    }

    /** ToneMapImage version followed by ISO 21496-1 GainMapMetadata (62/142 bytes). */
    fun strictToneMap(): ByteArray {
        val buffer = ByteBuffer.allocate(6 + 16 + channelCount * 40).order(ByteOrder.BIG_ENDIAN)
        buffer.put(0) // version
        buffer.putShort(0) // minimum version
        buffer.putShort(0) // writer version
        buffer.put(if (channelCount == 3) 0xc0.toByte() else 0x40.toByte())
        buffer.rational(capacityMinLog2)
        buffer.rational(capacityMaxLog2)
        repeat(channelCount) { index ->
            buffer.rational(gainMinLog2[index])
            buffer.rational(gainMaxLog2[index])
            buffer.rational(gamma[index])
            buffer.rational(offsetSdr[index])
            buffer.rational(offsetHdr[index])
        }
        return buffer.array()
    }

    private fun ByteBuffer.rational(value: Float) {
        val numerator = (value.toDouble() * DENOMINATOR).roundToLong()
        require(numerator >= Int.MIN_VALUE.toLong() && numerator <= Int.MAX_VALUE.toLong()) {
            "Gain-map rational exceeds 32 bits"
        }
        putInt(numerator.toInt())
        putInt(DENOMINATOR)
    }

    companion object {
        private const val DENOMINATOR = 100_000

        fun parse(bytes: ByteArray): IsoGainMapMetadata {
            require(bytes.size in setOf(62, 142))
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            require(b.get().toInt() == 0 && b.short.toInt() == 0 && b.short.toInt() == 0)
            val flags = b.get().toInt() and 255
            require(flags == 0x40 || flags == 0xc0) { "Unsupported gain-map color space" }
            val channels = if (flags == 0xc0) 3 else 1
            require(bytes.size == 22 + channels * 40)
            fun rational(): Float { val n = b.int; val d = b.int.toLong() and 0xffffffffL; require(d > 0); return (n.toDouble() / d).toFloat() }
            val min = rational(); val max = rational()
            val values = List(channels) { List(5) { rational() } }
            return IsoGainMapMetadata(values.map { it[0] }, values.map { it[1] }, values.map { it[2] },
                values.map { it[3] }, values.map { it[4] }, min, max)
        }

        fun fromRatios(
            ratioMin: FloatArray, ratioMax: FloatArray, gamma: FloatArray,
            offsetSdr: FloatArray, offsetHdr: FloatArray,
            minDisplayRatio: Float, fullHdrRatio: Float,
        ): IsoGainMapMetadata {
            val vectors = listOf(ratioMin, ratioMax, gamma, offsetSdr, offsetHdr)
            require(vectors.all { it.size == 3 && it.all(Float::isFinite) })
            require(ratioMin.all { it > 0 } && ratioMax.all { it > 0 })
            require(minDisplayRatio.isFinite() && fullHdrRatio.isFinite() &&
                minDisplayRatio >= 1 && fullHdrRatio >= minDisplayRatio)
            val monochrome = vectors.all { vector -> vector[0] == vector[1] && vector[0] == vector[2] }
            val count = if (monochrome) 1 else 3
            fun FloatArray.channels() = take(count)
            return IsoGainMapMetadata(
                ratioMin.channels().map { log2(it) }, ratioMax.channels().map { log2(it) },
                gamma.channels().map { 1f / it }, offsetSdr.channels(), offsetHdr.channels(),
                log2(minDisplayRatio), log2(fullHdrRatio),
            )
        }
    }
}
