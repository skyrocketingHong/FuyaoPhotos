package ing.fuyaoskyrocket.photoinfo

import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoMotionTokens
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoReassemblyGeometry
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class PhotoReassemblyCurveTest {
    @Test fun denseSamplesNeverReverseTravelColorOrOvershootTheSettleFrame() {
        val terminal = PhotoReassemblyGeometry.Progress(1f, 0f, 1f, 1f)
        var previous = PhotoReassemblyGeometry.progress(0f)
        for (index in 1..4000) {
            val t = index / 4000f
            val sample = PhotoReassemblyGeometry.progress(t)
            assertTrue(sample.travel.isFinite() && sample.scatter.isFinite() && sample.color.isFinite())
            assertTrue(sample.travel in 0f..1f && sample.scatter in 0f..1f && sample.color in 0f..1f)
            assertTrue(sample.cellScale in .45f..1f)
            assertTrue(sample.travel >= previous.travel && sample.color >= previous.color)
            if (t <= PhotoMotionTokens.reassemblyTravelStart) assertTrue(sample.scatter >= previous.scatter)
            else assertTrue(sample.scatter <= previous.scatter)
            if (t >= PhotoMotionTokens.reassemblySettleStart) assertEquals(terminal, sample)
            previous = sample
        }
    }

    @Test fun breakupRespondsQuicklyAndGatheringAcceleratesThenSettles() {
        val start = PhotoMotionTokens.reassemblyTravelStart
        val end = PhotoMotionTokens.reassemblySettleStart
        val quarterBreakup = PhotoReassemblyGeometry.progress(start * .25f).scatter
        val halfwayBreakup = PhotoReassemblyGeometry.progress(start * .5f).scatter
        assertTrue(quarterBreakup > .7f)
        assertTrue(halfwayBreakup - quarterBreakup < quarterBreakup)

        fun travel(fraction: Float) = PhotoReassemblyGeometry.progress(start + (end - start) * fraction).travel
        assertTrue(travel(.25f) < .2f)
        assertTrue(travel(.75f) > .8f)
        val firstQuarter = travel(.25f) - travel(0f)
        val middleHalf = travel(.75f) - travel(.25f)
        val finalQuarter = travel(1f) - travel(.75f)
        assertTrue(middleHalf > 3f * firstQuarter && middleHalf > 3f * finalQuarter)
        assertTrue((travel(.55f) - travel(.45f)) / .1f > 3f)
        for (index in 5..35) {
            val t = index / 100f
            assertTrue(travel(t + .01f) - 2f * travel(t) + travel(t - .01f) > 0f)
        }
        for (index in 65..95) {
            val t = index / 100f
            assertTrue(travel(t + .01f) - 2f * travel(t) + travel(t - .01f) < 0f)
        }

        val epsilon = .0001f
        val beforePeak = PhotoReassemblyGeometry.progress(start - epsilon).scatter
        val afterPeak = PhotoReassemblyGeometry.progress(start + epsilon).scatter
        assertTrue(abs(1f - beforePeak) / epsilon < .02f)
        assertTrue(abs(1f - afterPeak) / epsilon < .02f)
        assertTrue((travel(.001f) - travel(0f)) / .001f < .02f)
        assertTrue((travel(1f) - travel(.999f)) / .001f < .02f)
        fun color(fraction: Float) = PhotoReassemblyGeometry.progress(PhotoMotionTokens.reassemblyColorStart +
            (PhotoMotionTokens.reassemblyColorEnd - PhotoMotionTokens.reassemblyColorStart) * fraction).color
        assertTrue((color(.001f) - color(0f)) / .001f < .02f)
        assertTrue((color(1f) - color(.999f)) / .001f < .02f)
    }
}
