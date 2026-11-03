package io.github.voidcast00.noisesleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

class NoiseGeneratorTest {
    private val colors = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    private val warmths = listOf(0f, 0.35f, 0.7f, 1f)

    /** Renders [seconds] at full volume; returns (rms, peak). The first second is skipped to settle. */
    private fun measure(color: Float, warmth: Float, seconds: Int = 20, waveDb: Float = 0f): Pair<Double, Double> {
        val gen = NoiseGenerator(42)
        val buf = FloatArray(2048)
        val buffers = seconds * NoiseGenerator.SAMPLE_RATE / 1024
        var sum = 0.0
        var n = 0
        var peak = 0.0
        repeat(buffers) { b ->
            gen.render(buf, color, warmth, 1f, waveDepthDb = waveDb, waveSeconds = 6f)
            if (b < NoiseGenerator.SAMPLE_RATE / 1024) return@repeat
            for (x in buf) {
                sum += x * x; n++
                peak = max(peak, abs(x.toDouble()))
            }
        }
        return sqrt(sum / n) to peak
    }

    private fun weights(color: Float): Triple<Float, Float, Float> {
        val w = max(0f, 1f - 2f * color)
        val b = max(0f, 2f * color - 1f)
        return Triple(w, 1f - w - b, b)
    }

    @Test
    fun loudnessModelMatchesTheRealOutput() {
        // If the filter maths in ToneModel is right, its predicted RMS equals the measured one
        for (color in colors) for (warmth in warmths) {
            val (w, p, b) = weights(color)
            val fc = NoiseGenerator.cutoffHz(warmth)
            val predicted = sqrt(ToneModel.power(w, p, b, fc, weighted = false)) * ToneModel.gain(w, p, b, fc)
            val (measured, _) = measure(color, warmth)
            val errorDb = 20 * log10(measured / predicted)
            println("color=$color warmth=$warmth predicted=%.4f measured=%.4f error=%.2f dB".format(predicted, measured, errorDb))
            assertTrue("model off by $errorDb dB at color=$color warmth=$warmth", abs(errorDb) < 0.5)
        }
    }

    @Test
    fun toneSlidersDoNotChangeTheALevel() {
        var capped = 0
        val levels = mutableListOf<Double>()
        for (color in colors) for (warmth in warmths) {
            val (w, p, b) = weights(color)
            val fc = NoiseGenerator.cutoffHz(warmth)
            val g = ToneModel.gain(w, p, b, fc)
            val db = 10 * log10(ToneModel.power(w, p, b, fc, weighted = true)) + 20 * log10(g.toDouble())
            levels += db
            if (g >= 2.79f || g <= 0.251f) capped++
            println("color=$color warmth=$warmth gain=%.2f A-level=%.2f dB".format(g, db))
        }
        val reference = levels.max()
        val uncapped = levels.count { reference - it < 0.05 }
        println("within 0.05 dB of each other: $uncapped of ${levels.size}, capped: $capped")
        // Only very deep settings may fall short (white noise through a 250 Hz filter is nearly
        // silent on the A-scale, and boosting it further would just be loud rumble)
        assertTrue("too many settings capped", uncapped >= levels.size - 5)
        assertTrue("capped settings drop too far", levels.all { reference - it < 16.0 })
    }

    @Test
    fun noSettingClipsAtFullVolume() {
        for (color in colors) for (warmth in warmths) {
            val (rms, peak) = measure(color, warmth, seconds = 10)
            println("color=$color warmth=$warmth rms=%.3f peak=%.3f".format(rms, peak))
            assertTrue("too quiet: color=$color warmth=$warmth", rms > 0.01)
            // The engine's soft limiter rounds off anything above 0.9; rare peaks just above
            // that are inaudible, but nothing should come near full scale
            assertTrue("peak $peak at color=$color warmth=$warmth", peak < 0.97)
        }
    }

    @Test
    fun wavesOnlyEverMakeItQuieter() {
        for (color in listOf(0f, 0.5f, 1f)) {
            val (flat, flatPeak) = measure(color, 0.35f)
            val (wavy, wavyPeak) = measure(color, 0.35f, waveDb = 9f)
            println("color=$color flat rms=%.3f waves rms=%.3f peak=%.3f".format(flat, wavy, wavyPeak))
            assertTrue("waves should lower the average level", wavy < flat)
            assertTrue("waves must not be louder than the setting", wavyPeak <= flatPeak * 1.05)
        }
    }

    @Test
    fun waveStrengthStepsAreInDecibels() {
        assertEquals(1.5f, NoiseGenerator.waveDepthDb(0f))
        assertEquals(3f, NoiseGenerator.waveDepthDb(0.25f)) // the default
        assertEquals(9f, NoiseGenerator.waveDepthDb(1f))
    }

    @Test
    fun waveShapeSwellsAndReturns() {
        assertEquals(0f, NoiseGenerator.waveLevel(0f), 1e-4f)
        assertEquals(1f, NoiseGenerator.waveLevel(0.4f), 1e-4f) // crest 40 % of the way in
        assertEquals(0f, NoiseGenerator.waveLevel(1f), 1e-4f)
        assertTrue(NoiseGenerator.waveLevel(0.2f) in 0.4f..0.6f)
    }

    @Test
    fun softClipIsTransparentBelowItsKnee() {
        assertEquals(0.5f, NoiseGenerator.softClip(0.5f))
        assertEquals(-0.85f, NoiseGenerator.softClip(-0.85f))
        assertTrue(NoiseGenerator.softClip(3f) <= 1f && NoiseGenerator.softClip(0.95f) < 0.95f)
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
