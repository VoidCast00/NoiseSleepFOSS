package io.github.voidcast00.noisesleep

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class NoiseGeneratorTest {
    /** Renders [seconds] of audio and returns (rms, peak). The first second is skipped to let filters settle. */
    private fun measure(color: Float, warmth: Float, seconds: Int = 20): Pair<Double, Double> {
        val gen = NoiseGenerator(42)
        val buf = FloatArray(2048)
        val buffers = seconds * NoiseGenerator.SAMPLE_RATE / 1024
        var sum = 0.0
        var n = 0
        var peak = 0.0
        repeat(buffers) { b ->
            gen.render(buf, color, warmth, 1f)
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
    fun leftAndRightAreIndependent() {
        val buf = FloatArray(2048)
        NoiseGenerator(7).render(buf, 0.5f, 0f, 1f)
        val left = buf.filterIndexed { i, _ -> i % 2 == 0 }
        val right = buf.filterIndexed { i, _ -> i % 2 == 1 }
        assertNotEquals(left, right)
    }
}
