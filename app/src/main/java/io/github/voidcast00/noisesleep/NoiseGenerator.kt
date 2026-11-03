package io.github.voidcast00.noisesleep

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure DSP, no Android classes, so it can be unit tested on the JVM.
 *
 * Colour blends white (0) → pink (0.5) → brown (1). Warmth is a low-pass filter that takes the
 * hiss off the top. A fixed 30 Hz high-pass removes rumble phones cannot play anyway. Left and
 * right use separate random sources, so the sound is wide instead of coming from a single point.
 * Every parameter glides to its new value to avoid clicks.
 *
 * Colour and warmth change only the character of the sound, not its loudness: the output is
 * scaled so its A-weighted level (dB(A), the measure sleep studies and the WHO use) stays the
 * same, see [ToneModel].
 *
 * Ocean waves slowly swell the volume (and slightly the brightness) up and down. Each wave builds
 * up faster than it washes out, and every wave is a little longer or shorter than the last.
 */
class NoiseGenerator(seed: Long) {
    private val left = Channel(seed)
    private val right = Channel(seed * 6364136223846793005L + 1442695040888963407L)
    private val lowPass = Biquad()
    private val highPass = Biquad().apply { setHighPass(HIGH_PASS_HZ) }

    private var started = false
    private var wWhite = 0f
    private var wPink = 0f
    private var wBrown = 0f
    private var gain = 0f
    private var warmth = 0f
    private val loudness = ToneModel.Cache()

    private val waveRandom = java.util.Random(seed xor 0x5DEECE66DL)
    private var wavePhase = 0.0      // 0..1 through the current wave
    private var waveStep = 0.0       // phase per sample
    private var waveSeconds = 0f     // the setting the current step was made from
    private var waveDb = 0f          // glides to the wave depth, or to 0 when waves are off

    /**
     * Fills [out] with interleaved stereo samples.
     * [waveDepthDb]: how far the troughs dip below the set volume. 0 = no waves.
     */
    fun render(
        out: FloatArray,
        color: Float,
        warmth: Float,
        gain: Float,
        waveDepthDb: Float = 0f,
        waveSeconds: Float = 12f,
    ) {
        val c = color.coerceIn(0f, 1f)
        val tWhite = max(0f, 1f - 2f * c)
        val tBrown = max(0f, 2f * c - 1f)
        val tPink = 1f - tWhite - tBrown
        val tWarmth = warmth.coerceIn(0f, 1f)

        val first = !started
        if (first) {
            started = true
            wWhite = tWhite; wPink = tPink; wBrown = tBrown
            this.warmth = tWarmth
        }
        // Waves move slowly, so once per buffer is plenty: the per-sample gain glide smooths it
        val frames = out.size / 2
        if (waveSeconds != this.waveSeconds) newWave(waveSeconds)
        wavePhase += waveStep * frames
        if (wavePhase >= 1.0) {
            wavePhase -= 1.0
            newWave(waveSeconds)
        }
        waveDb += (waveDepthDb.coerceIn(0f, MAX_WAVE_DB) - waveDb) * 0.02f // ~1 s
        val dip = waveDb * (1f - waveLevel(wavePhase.toFloat())) // dB below the set volume now

        // The filter is updated once per buffer, so it glides per buffer rather than per sample.
        // Waves darken the troughs a little too (at most half an octave): mostly volume moves.
        this.warmth += (tWarmth - this.warmth) * 0.15f
        val cutoff = cutoffHz(this.warmth)
        lowPass.setLowPass(max(120.0, cutoff * 2.0.pow(-0.5 * dip / MAX_WAVE_DB)))

        // Level from the un-dipped tone, so the waves still sound like waves
        val level = loudness.gain(wWhite, wPink, wBrown, cutoff)
        val tGain = gain * level * 10f.pow(-dip / 20f)
        if (first) this.gain = tGain

        var i = 0
        while (i + 1 < out.size) {
            wWhite += (tWhite - wWhite) * SMOOTH
            wPink += (tPink - wPink) * SMOOTH
            wBrown += (tBrown - wBrown) * SMOOTH
            this.gain += (tGain - this.gain) * SMOOTH
            out[i] = (left.next(wWhite, wPink, wBrown, lowPass, highPass) * this.gain).toFloat()
            out[i + 1] = (right.next(wWhite, wPink, wBrown, lowPass, highPass) * this.gain).toFloat()
            i += 2
        }
    }

    /** Each wave lasts the set length, give or take 25 %. */
    private fun newWave(seconds: Float) {
        this.waveSeconds = seconds
        val length = seconds.coerceIn(2f, 60f) * (0.75 + 0.5 * waveRandom.nextDouble())
        waveStep = 1.0 / (length * SAMPLE_RATE)
    }

    companion object {
        /** The pink filter coefficients are tuned for 44.1 kHz. */
        const val SAMPLE_RATE = 44100

        const val HIGH_PASS_HZ = 30.0

        /** Wave strength steps, in dB from crest to trough. The research suggests 0–6, default 3. */
        val WAVE_STEPS_DB = floatArrayOf(1.5f, 3f, 4.5f, 6f, 9f)
        val MAX_WAVE_DB = WAVE_STEPS_DB.last()

        /** The stored 0..1 strength setting → one of [WAVE_STEPS_DB]. */
        fun waveStep(strength: Float): Int = (strength.coerceIn(0f, 1f) * (WAVE_STEPS_DB.size - 1)).roundToInt()
        fun waveDepthDb(strength: Float): Float = WAVE_STEPS_DB[waveStep(strength)]

        /**
         * Wave height (0..1) at [phase] (0..1): a cosine swell that peaks 40 % of the way in,
         * so it builds up quicker than it washes out.
         */
        fun waveLevel(phase: Float): Float {
            val p = phase - kotlin.math.floor(phase)
            val shaped = if (p < 0.4f) p / 0.4f * 0.5f else 0.5f + (p - 0.4f) / 0.6f * 0.5f
            return (0.5 - 0.5 * cos(2.0 * PI * shaped)).toFloat()
        }

        /** Linear up to 0.9, then rounds off smoothly instead of clipping hard. */
        fun softClip(x: Float): Float {
            val a = abs(x)
            if (a <= 0.9f) return x
            val y = 0.9f + 0.1f * kotlin.math.tanh((a - 0.9f) / 0.1f)
            return if (x < 0f) -y else y
        }

        /** ~50 ms glide for volume and colour changes. */
        private val SMOOTH = (1.0 - exp(-1.0 / (SAMPLE_RATE * 0.05))).toFloat()

        // Raw gains for each colour. Loudness is evened out by ToneModel afterwards; these only
        // set the balance of a blend (e.g. how pink a pink-brown mix sounds).
        internal const val WHITE_GAIN = 0.17
        internal const val PINK_GAIN = 0.075
        internal const val BROWN_GAIN = 3.0

        /** Warmth 0 → 18 kHz (practically no filtering), warmth 1 → 250 Hz (deep and muffled). */
        fun cutoffHz(warmth: Float): Double = 18000.0 * (250.0 / 18000.0).pow(warmth.toDouble())

        /** The warmth value whose cutoff is [hz]. */
        fun warmthFor(hz: Double): Float = (ln(hz / 18000.0) / ln(250.0 / 18000.0)).toFloat()
    }

    private class Channel(seed: Long) {
        private var rng = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed
        private var p0 = 0.0; private var p1 = 0.0; private var p2 = 0.0; private var p3 = 0.0
        private var p4 = 0.0; private var p5 = 0.0; private var p6 = 0.0
        private var brown = 0.0
        private val lpState = DoubleArray(4)
        private val hpState = DoubleArray(4)

        fun next(wWhite: Float, wPink: Float, wBrown: Float, lp: Biquad, hp: Biquad): Double {
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
            return lp.process(hp.process(x, hpState), lpState)
        }
    }
}

/** 2nd-order Butterworth filter (RBJ cookbook). Coefficients are shared, state is per channel. */
internal class Biquad {
    var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
    private var cutoff = -1.0

    fun setLowPass(fc: Double) {
        if (abs(fc - cutoff) < 0.5) return
        cutoff = fc
        val (cosW, alpha) = prepare(fc)
        val a0 = 1.0 + alpha
        b0 = (1.0 - cosW) / 2.0 / a0
        b1 = (1.0 - cosW) / a0
        b2 = b0
        a1 = -2.0 * cosW / a0
        a2 = (1.0 - alpha) / a0
    }

    fun setHighPass(fc: Double) {
        cutoff = fc
        val (cosW, alpha) = prepare(fc)
        val a0 = 1.0 + alpha
        b0 = (1.0 + cosW) / 2.0 / a0
        b1 = -(1.0 + cosW) / a0
        b2 = b0
        a1 = -2.0 * cosW / a0
        a2 = (1.0 - alpha) / a0
    }

    private fun prepare(fc: Double): Pair<Double, Double> {
        val w0 = 2.0 * PI * fc / NoiseGenerator.SAMPLE_RATE
        return cos(w0) to sin(w0) / (2.0 * 0.7071067811865476)
    }

    /** [s] holds x1, x2, y1, y2. */
    fun process(x: Double, s: DoubleArray): Double {
        val y = b0 * x + b1 * s[0] + b2 * s[1] - a1 * s[2] - a2 * s[3]
        s[1] = s[0]; s[0] = x
        s[3] = s[2]; s[2] = y
        return y
    }

    /** |H|² at angular frequency [w] (radians per sample). */
    fun power(w: Double): Double {
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b0 + b1 * c1 + b2 * c2; val ni = -(b1 * s1 + b2 * s2)
        val dr = 1.0 + a1 * c1 + a2 * c2; val di = -(a1 * s1 + a2 * s2)
        return (nr * nr + ni * ni) / (dr * dr + di * di)
    }
}

/**
 * Works out, from the filter maths rather than by listening, how loud a colour/warmth setting
 * is, so the generator can keep its A-weighted level steady.
 *
 * The output is white noise passed through linear filters, so its power is the white noise
 * power times the integral of |H(f)|² over frequency. Weighting that by the A-curve gives a
 * figure proportional to dB(A). Digital levels are still not calibrated sound levels at your
 * ears; this only stops the tone sliders from changing the loudness.
 */
internal object ToneModel {
    private const val WHITE_POWER = 1.0 / 3.0 // uniform noise in [-1, 1]

    /** Boost is capped so deep settings don't turn into loud, clipped bass. */
    private const val MAX_GAIN = 2.8 // +9 dB
    private const val MIN_GAIN = 0.25

    private const val POINTS = 220
    private val freqs = DoubleArray(POINTS) { i -> 10.0 * (21000.0 / 10.0).pow(i / (POINTS - 1.0)) }
    private val widths = DoubleArray(POINTS) { i ->
        val lo = freqs[maxOf(i - 1, 0)]; val hi = freqs[minOf(i + 1, POINTS - 1)]
        (hi - lo) / 2.0
    }
    private val aWeight = DoubleArray(POINTS) { i -> aWeightPower(freqs[i]) }
    private val highPass = Biquad().apply { setHighPass(NoiseGenerator.HIGH_PASS_HZ) }

    // Declared before `reference`, which needs them while the object initialises
    private val PINK_POLES = doubleArrayOf(0.99886, 0.99332, 0.96900, 0.86650, 0.55000, -0.7616)
    private val PINK_GAINS = doubleArrayOf(0.0555179, 0.0750759, 0.1538520, 0.3104856, 0.5329522, -0.0168980)

    /** Reference: pink noise with the low-pass at 4 kHz plays at the raw [LEVEL]. */
    private val reference = power(0f, 1f, 0f, 4000.0, weighted = true)

    /** Overall output level. Chosen so even the boosted extremes keep their peaks below 1. */
    private const val LEVEL = 0.7

    fun gain(wWhite: Float, wPink: Float, wBrown: Float, cutoff: Double): Float {
        val p = power(wWhite, wPink, wBrown, cutoff, weighted = true)
        return (LEVEL * sqrt(reference / p)).coerceIn(MIN_GAIN, MAX_GAIN).toFloat()
    }

    /** Output power for one channel, before the gain. */
    fun power(wWhite: Float, wPink: Float, wBrown: Float, cutoff: Double, weighted: Boolean): Double {
        val lp = Biquad().apply { setLowPass(cutoff) }
        var sum = 0.0
        for (i in 0 until POINTS) {
            val w = 2.0 * PI * freqs[i] / NoiseGenerator.SAMPLE_RATE
            val c = cos(w); val s = sin(w)
            // Pink: Kellet's sum of one-pole filters c/(1 - a e^-jw), plus a delay and a direct term
            var pr = 0.5362 + 0.115926 * c
            var pi = -0.115926 * s
            for (k in PINK_POLES.indices) {
                val a = PINK_POLES[k]; val g = PINK_GAINS[k]
                val dr = 1.0 - a * c; val di = a * s
                val d = dr * dr + di * di
                pr += g * dr / d; pi -= g * di / d
            }
            // Brown: (0.02/1.02) / (1 - e^-jw/1.02)
            val ab = 1.0 / 1.02; val gb = 0.02 / 1.02
            val bdr = 1.0 - ab * c; val bdi = ab * s
            val bd = bdr * bdr + bdi * bdi
            val br = gb * bdr / bd; val bi = -gb * bdi / bd

            val hr = wWhite * NoiseGenerator.WHITE_GAIN + wPink * NoiseGenerator.PINK_GAIN * pr +
                wBrown * NoiseGenerator.BROWN_GAIN * br
            val hi = wPink * NoiseGenerator.PINK_GAIN * pi + wBrown * NoiseGenerator.BROWN_GAIN * bi
            val h = (hr * hr + hi * hi) * lp.power(w) * highPass.power(w)
            sum += h * widths[i] * (if (weighted) aWeight[i] else 1.0)
        }
        return WHITE_POWER * 2.0 / NoiseGenerator.SAMPLE_RATE * sum
    }

    /** IEC 61672 A-weighting, as a power ratio. */
    private fun aWeightPower(f: Double): Double {
        val f2 = f * f
        val ra = 12194.0 * 12194.0 * f2 * f2 /
            ((f2 + 20.6 * 20.6) * sqrt((f2 + 107.7 * 107.7) * (f2 + 737.9 * 737.9)) * (f2 + 12194.0 * 12194.0))
        return ra * ra * 1.2589 * 1.2589 // +2 dB so 1 kHz is 0 dB
    }

    /** Recomputes only when the tone has actually moved. */
    class Cache {
        private var w = floatArrayOf(-1f, -1f, -1f)
        private var fc = -1.0
        private var value = 1f

        fun gain(wWhite: Float, wPink: Float, wBrown: Float, cutoff: Double): Float {
            if (abs(wWhite - w[0]) > 1e-3f || abs(wPink - w[1]) > 1e-3f || abs(wBrown - w[2]) > 1e-3f ||
                abs(cutoff - fc) > cutoff * 1e-3
            ) {
                w = floatArrayOf(wWhite, wPink, wBrown)
                fc = cutoff
                value = ToneModel.gain(wWhite, wPink, wBrown, cutoff)
            }
            return value
        }
    }
}
