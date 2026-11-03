package io.github.voidcast00.noisesleep

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pure DSP, no Android classes, so it can be unit tested on the JVM.
 *
 * Colour blends white (0) → pink (0.5) → brown (1). Warmth is a low-pass filter that takes the
 * hiss off the top. Left and right use separate random sources, so the sound is wide instead of
 * coming from a single point. Every parameter glides to its new value to avoid clicks.
 */
class NoiseGenerator(seed: Long) {
    private val left = Channel(seed)
    private val right = Channel(seed * 6364136223846793005L + 1442695040888963407L)
    private val lowPass = LowPass()

    private var started = false
    private var wWhite = 0f
    private var wPink = 0f
    private var wBrown = 0f
    private var gain = 0f
    private var warmth = 0f

    /** Fills [out] with interleaved stereo samples. */
    fun render(out: FloatArray, color: Float, warmth: Float, gain: Float) {
        val c = color.coerceIn(0f, 1f)
        val tWhite = max(0f, 1f - 2f * c)
        val tBrown = max(0f, 2f * c - 1f)
        val tPink = 1f - tWhite - tBrown
        val tWarmth = warmth.coerceIn(0f, 1f)

        if (!started) {
            started = true
            wWhite = tWhite; wPink = tPink; wBrown = tBrown
            this.gain = gain
            this.warmth = tWarmth
        }
        // The filter is updated once per buffer, so it glides per buffer rather than per sample
        this.warmth += (tWarmth - this.warmth) * 0.15f
        lowPass.set(cutoffHz(this.warmth))

        var i = 0
        while (i + 1 < out.size) {
            wWhite += (tWhite - wWhite) * SMOOTH
            wPink += (tPink - wPink) * SMOOTH
            wBrown += (tBrown - wBrown) * SMOOTH
            this.gain += (gain - this.gain) * SMOOTH
            out[i] = (left.next(wWhite, wPink, wBrown, lowPass) * this.gain).toFloat()
            out[i + 1] = (right.next(wWhite, wPink, wBrown, lowPass) * this.gain).toFloat()
            i += 2
        }
    }

    companion object {
        /** The pink filter coefficients are tuned for 44.1 kHz. */
        const val SAMPLE_RATE = 44100

        /** ~50 ms glide for volume and colour changes. */
        private val SMOOTH = (1.0 - exp(-1.0 / (SAMPLE_RATE * 0.05))).toFloat()

        // Each colour has a very different raw level. These even them out, with white kept a
        // little lower and brown a little higher because the ear hears high frequencies as louder.
        private const val WHITE_GAIN = 0.17   // uniform noise, RMS 0.577 → ~0.10
        private const val PINK_GAIN = 0.075   // Kellet filter, RMS 1.75 → ~0.13
        private const val BROWN_GAIN = 3.0    // leaky integrator, RMS 0.057 → ~0.17

        /** Warmth 0 → 18 kHz (practically no filtering), warmth 1 → 250 Hz (deep and muffled). */
        fun cutoffHz(warmth: Float): Double = 18000.0 * (250.0 / 18000.0).pow(warmth.toDouble())
    }

    private class Channel(seed: Long) {
        private var rng = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed
        private var p0 = 0.0; private var p1 = 0.0; private var p2 = 0.0; private var p3 = 0.0
        private var p4 = 0.0; private var p5 = 0.0; private var p6 = 0.0
        private var brown = 0.0
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

        fun next(wWhite: Float, wPink: Float, wBrown: Float, lp: LowPass): Double {
            // xorshift64*: fast, and with no locking (java.util.Random synchronises)
            rng = rng xor (rng ushr 12)
            rng = rng xor (rng shl 25)
            rng = rng xor (rng ushr 27)
            val bits = (rng * 2685821657736338717L) ushr 11
            val white = bits * (2.0 / (1L shl 53)) - 1.0

            // Paul Kellet's pink noise filter (-3 dB per octave)
            p0 = 0.99886 * p0 + white * 0.0555179
            p1 = 0.99332 * p1 + white * 0.0750759
            p2 = 0.96900 * p2 + white * 0.1538520
            p3 = 0.86650 * p3 + white * 0.3104856
            p4 = 0.55000 * p4 + white * 0.5329522
            p5 = -0.7616 * p5 - white * 0.0168980
            val pink = p0 + p1 + p2 + p3 + p4 + p5 + p6 + white * 0.5362
            p6 = white * 0.115926

            // Brown noise: leaky integrator (-6 dB per octave above ~140 Hz)
            brown = (brown + 0.02 * white) / 1.02

            val x = wWhite * white * WHITE_GAIN + wPink * pink * PINK_GAIN + wBrown * brown * BROWN_GAIN
            val y = lp.b0 * x + lp.b1 * x1 + lp.b2 * x2 - lp.a1 * y1 - lp.a2 * y2
            x2 = x1; x1 = x
            y2 = y1; y1 = y
            return y
        }
    }

    /** 2nd-order Butterworth low-pass (RBJ cookbook), shared by both channels. */
    private class LowPass {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
        private var cutoff = -1.0

        fun set(fc: Double) {
            if (kotlin.math.abs(fc - cutoff) < 0.5) return
            cutoff = fc
            val w0 = 2.0 * PI * fc / SAMPLE_RATE
            val cosW = cos(w0)
            val alpha = sin(w0) / (2.0 * 0.7071067811865476)
            val a0 = 1.0 + alpha
            b0 = (1.0 - cosW) / 2.0 / a0
            b1 = (1.0 - cosW) / a0
            b2 = b0
            a1 = -2.0 * cosW / a0
            a2 = (1.0 - alpha) / a0
        }
    }
}
