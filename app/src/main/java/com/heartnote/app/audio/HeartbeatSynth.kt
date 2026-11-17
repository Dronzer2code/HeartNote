package com.heartnote.app.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * TRD § HEARTBEAT SYNTHESIS: a lub-dub per beat, 48 kHz mono float.
 *
 * Lub: 55 Hz, τ 40 ms, 120 ms, gain 1.0. Dub: 70 Hz, τ 30 ms, 90 ms, gain 0.6,
 * at t + clamp(0.3 × RR, 200, 350) ms. One-pole low-pass at 150 Hz.
 * [SPEAKER_HARMONICS] adds quieter 2nd–4th harmonics so phone speakers, which
 * reproduce little below ~200 Hz, still render the thump (missing-fundamental
 * effect). The fundamentals and envelopes are unchanged.
 */
object HeartbeatSynth {
    const val SAMPLE_RATE = 48_000
    const val SPEAKER_HARMONICS = true

    private const val LOWPASS_HZ = 150.0
    private const val ATTACK_MS = 3.0
    private val HARMONIC_GAINS = doubleArrayOf(1.0, 0.5, 0.3, 0.15)

    private val lub: FloatArray by lazy { template(freqHz = 55.0, tauMs = 40.0, lengthMs = 120.0, gain = 1.0) }
    private val dub: FloatArray by lazy { template(freqHz = 70.0, tauMs = 30.0, lengthMs = 90.0, gain = 0.6) }

    /** Same rule as playback haptics and the live sphere (DESIGN.md § BEAT SYNC). */
    fun dubDelayMs(rrMs: Long): Long = (0.3 * rrMs).roundToInt().toLong().coerceIn(200L, 350L)

    /** Interval to the next beat; the last beat reuses the previous interval. */
    fun rrAt(beatsMs: List<Long>, index: Int): Long = when {
        beatsMs.size < 2 -> 800L
        index < beatsMs.size - 1 -> beatsMs[index + 1] - beatsMs[index]
        else -> beatsMs[index] - beatsMs[index - 1]
    }

    fun render(beatsMs: List<Long>, durationMs: Long): FloatArray {
        val out = FloatArray(msToSamples(durationMs.toDouble()))
        beatsMs.forEachIndexed { i, beat ->
            add(out, lub, msToSamples(beat.toDouble()))
            add(out, dub, msToSamples((beat + dubDelayMs(rrAt(beatsMs, i))).toDouble()))
        }
        return out
    }

    private fun add(out: FloatArray, template: FloatArray, start: Int) {
        for (k in template.indices) {
            val i = start + k
            if (i in out.indices) out[i] += template[k]
        }
    }

    /** One beat sound, low-passed (with tail) and peak-normalised to [gain]. */
    private fun template(freqHz: Double, tauMs: Double, lengthMs: Double, gain: Double): FloatArray {
        val n = msToSamples(lengthMs + 30.0) // + low-pass tail
        val sound = msToSamples(lengthMs)
        val attack = msToSamples(ATTACK_MS)
        val harmonics = if (SPEAKER_HARMONICS) HARMONIC_GAINS.size else 1
        val raw = DoubleArray(n)
        for (i in 0 until sound) {
            val t = i.toDouble() / SAMPLE_RATE
            var v = 0.0
            for (h in 0 until harmonics) v += HARMONIC_GAINS[h] * sin(2 * PI * freqHz * (h + 1) * t)
            val env = exp(-t * 1000.0 / tauMs)
            val fadeIn = if (i < attack) 0.5 - 0.5 * cos(PI * i / attack) else 1.0
            val fadeOut = if (i > sound - attack) (sound - i).toDouble() / attack else 1.0
            raw[i] = v * env * fadeIn * fadeOut
        }
        // One-pole low-pass.
        val a = exp(-2 * PI * LOWPASS_HZ / SAMPLE_RATE)
        var y = 0.0
        for (i in 0 until n) {
            y = (1 - a) * raw[i] + a * y
            raw[i] = y
        }
        val peak = raw.maxOf { abs(it) }.takeIf { it > 0 } ?: 1.0
        return FloatArray(n) { (raw[it] / peak * gain).toFloat() }
    }

    fun msToSamples(ms: Double): Int = (ms * SAMPLE_RATE / 1000.0).roundToInt()
}
