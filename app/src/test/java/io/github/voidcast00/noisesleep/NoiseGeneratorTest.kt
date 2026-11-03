package io.github.voidcast00.noisesleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class NoiseGeneratorTest {
    /** Renders [seconds] of audio and returns (rms, peak). The first second is skipped to let filters settle. */
    private fun measure(color: Float, warmth: Float, seconds: Int = 20, waves: Float = 0f): Pair<Double, Double> {
        val gen = NoiseGenerator(42)
        val buf = FloatArray(2048)
        val buffers = seconds * NoiseGenerator.SAMPLE_RATE / 1024
        var sum = 0.0
        var n = 0
        var peak = 0.0
        repeat(buffers) { b ->
            gen.render(buf, color, warmth, 1f, waveStrength = waves, waveSeconds = 6f)
            if (b < NoiseGenerator.SAMPLE_RATE / 1024) return@repeat
            for (x in buf) {
                sum += x * x; n++
                peak = maxOf(peak, abs(x.toDouble()))
            }
        }
        return sqrt(sum / n) to peak
    }

    @Test
    fun everySettingIsAudibleAndNeverClipsAtFullVolume() {
        for (color in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            for (warmth in listOf(0f, 0.5f, 1f)) {
                val (rms, peak) = measure(color, warmth)
                println("color=$color warmth=$warmth rms=%.3f peak=%.3f".format(rms, peak))
                assertTrue("too quiet: color=$color warmth=$warmth rms=$rms", rms > 0.01)
                assertTrue("clips: color=$color warmth=$warmth peak=$peak", peak < 1.0)
            }
        }
    }

    @Test
    fun wavesOnlyEverMakeItQuieter() {
        for (color in listOf(0f, 0.5f, 1f)) {
            val (flat, flatPeak) = measure(color, 0.25f)
            val (wavy, wavyPeak) = measure(color, 0.25f, waves = 1f)
            println("color=$color flat rms=%.3f waves rms=%.3f peak=%.3f".format(flat, wavy, wavyPeak))
            assertTrue("waves should lower the average level", wavy < flat)
            assertTrue("waves must not clip", wavyPeak < 1.0)
            assertTrue("waves must not be louder than the setting", wavyPeak <= flatPeak * 1.05)
        }
    }

    @Test
    fun waveShapeSwellsAndReturns() {
        assertEquals(0f, NoiseGenerator.waveLevel(0f), 1e-4f)
        assertEquals(1f, NoiseGenerator.waveLevel(0.4f), 1e-4f) // crest 40 % of the way in
        assertEquals(0f, NoiseGenerator.waveLevel(1f), 1e-4f)
        assertTrue(NoiseGenerator.waveLevel(0.2f) in 0.4f..0.6f)
    }

    @Test
    fun leftAndRightAreIndependent() {
        val buf = FloatArray(2048)
        NoiseGenerator(7).render(buf, 0.5f, 0f, 1f)
        val left = buf.filterIndexed { i, _ -> i % 2 == 0 }
        val right = buf.filterIndexed { i, _ -> i % 2 == 1 }
        assertNotEquals(left, right)
    }
}
