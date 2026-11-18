package com.heartnote.app.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A lub-dub per beat, 48 kHz mono float.
 *
 * [Preset.SPEAKER] (SHARE_AND_IMPORT.md § AMPLIFIED HEARTBEAT, default for
 * every new mix): fundamental + 2nd (0.5) + 3rd (0.25) harmonics and an 8 ms
 * soft transient around 800 Hz (0.15) so the thump is audible on phone
 * speakers, which reproduce little below ~100 Hz. [Preset.CLASSIC] is the
 * original TRD sound (pure sine, 150 Hz one-pole low-pass).
 * Lub: 55 Hz, τ 40 ms, 120 ms. Dub: 70 Hz, τ 30 ms, 90 ms, 0.6 of the lub,
 * at t + clamp(0.3 × RR, 200, 350) ms.
 */
object HeartbeatSynth {
    const val SAMPLE_RATE = 48_000

    enum class Preset { SPEAKER, CLASSIC }

    private const val LOWPASS_HZ = 150.0
    private const val ATTACK_MS = 3.0
    private val SPEAKER_HARMONICS = doubleArrayOf(1.0, 0.5, 0.25)
    private const val TRANSIENT_HZ = 800.0
    private const val TRANSIENT_MS = 8.0
    private const val TRANSIENT_GAIN = 0.15

    private val templates = HashMap<Preset, Pair<FloatArray, FloatArray>>()

    @Synchronized
    private fun templatesFor(preset: Preset): Pair<FloatArray, FloatArray> = templates.getOrPut(preset) {
        template(preset, freqHz = 55.0, tauMs = 40.0, lengthMs = 120.0, gain = 1.0) to
            template(preset, freqHz = 70.0, tauMs = 30.0, lengthMs = 90.0, gain = 0.6)
    }

    /** Same rule as playback haptics and the live sphere (DESIGN.md § BEAT SYNC). */
    fun dubDelayMs(rrMs: Long): Long = (0.3 * rrMs).roundToInt().toLong().coerceIn(200L, 350L)

    /** Interval to the next beat; the last beat reuses the previous interval. */
    fun rrAt(beatsMs: List<Long>, index: Int): Long = when {
        beatsMs.size < 2 -> 800L
        index < beatsMs.size - 1 -> beatsMs[index + 1] - beatsMs[index]
        else -> beatsMs[index] - beatsMs[index - 1]
    }

    fun render(beatsMs: List<Long>, durationMs: Long, preset: Preset = Preset.SPEAKER): FloatArray {
        val (lub, dub) = templatesFor(preset)
        val out = FloatArray(msToSamples(durationMs.toDouble()))
        beatsMs.forEachIndexed { i, beat ->
            add(out, lub, msToSamples(beat.toDouble()))
            add(out, dub, msToSamples((beat + dubDelayMs(rrAt(beatsMs, i))).toDouble()))
        }
        return out
    }

    /** One lub template (beat recovery matches against it). */
    fun lubTemplate(preset: Preset = Preset.SPEAKER): FloatArray = templatesFor(preset).first

    private fun add(out: FloatArray, template: FloatArray, start: Int) {
        for (k in template.indices) {
            val i = start + k
            if (i in out.indices) out[i] += template[k]
        }
    }

    /** One beat sound, peak-normalised to [gain]. */
    private fun template(preset: Preset, freqHz: Double, tauMs: Double, lengthMs: Double, gain: Double): FloatArray {
        val classic = preset == Preset.CLASSIC
        val n = msToSamples(lengthMs + if (classic) 30.0 else 0.0) // classic: + low-pass tail
        val sound = msToSamples(lengthMs)
        val attack = msToSamples(ATTACK_MS)
        val transient = msToSamples(TRANSIENT_MS)
        val raw = DoubleArray(n)
        for (i in 0 until sound) {
            val t = i.toDouble() / SAMPLE_RATE
            var v = 0.0
            if (classic) {
                v = sin(2 * PI * freqHz * t)
            } else {
                for (h in SPEAKER_HARMONICS.indices) v += SPEAKER_HARMONICS[h] * sin(2 * PI * freqHz * (h + 1) * t)
            }
            val env = exp(-t * 1000.0 / tauMs)
            val fadeIn = if (i < attack) 0.5 - 0.5 * cos(PI * i / attack) else 1.0
            val fadeOut = if (i > sound - attack) (sound - i).toDouble() / attack else 1.0
            raw[i] = v * env * fadeIn * fadeOut
            if (!classic && i < transient) {
                // Hann-windowed 800 Hz burst: band-limited "knock" a small speaker can play.
                val w = 0.5 - 0.5 * cos(2 * PI * i / transient)
                raw[i] += TRANSIENT_GAIN * w * sin(2 * PI * TRANSIENT_HZ * t)
            }
        }
        if (classic) {
            val a = exp(-2 * PI * LOWPASS_HZ / SAMPLE_RATE)
            var y = 0.0
            for (i in 0 until n) {
                y = (1 - a) * raw[i] + a * y
                raw[i] = y
            }
        }
        val peak = raw.maxOf { abs(it) }.takeIf { it > 0 } ?: 1.0
        return FloatArray(n) { (raw[it] / peak * gain).toFloat() }
    }

    fun msToSamples(ms: Double): Int = (ms * SAMPLE_RATE / 1000.0).roundToInt()
}
