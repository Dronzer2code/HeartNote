package com.heartnote.app.share

import com.heartnote.app.audio.HeartbeatSynth
import com.heartnote.app.audio.Mixer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM proxy for § BEAT RECOVERY: a mixed note (speech-like voice + SPEAKER
 * heartbeat at the real mix gain), degraded the way a lossy re-encode does
 * (band limit, noise, 44.1 kHz resample), then recovered. The real target —
 * a WhatsApp media send — must still be measured on the phone.
 */
class BeatRecoveryTest {
    private val durationMs = 40_000L

    private fun beats(bpm: Double, seed: Int): List<Long> {
        val rnd = Random(seed)
        val out = ArrayList<Long>()
        var t = 400.0
        while (t < durationMs - 500) {
            out += t.toLong()
            t += 60_000 / bpm + (rnd.nextDouble() * 2 - 1) * 30
        }
        return out
    }

    /** Syllable-like voice: harmonic stacks at [minPitch]–[minPitch]+110 Hz, gated at ~4 Hz. */
    private fun voice(samples: Int, seed: Int, minPitch: Double = 110.0): ShortArray {
        val rnd = Random(seed)
        var pitch = minPitch + 30
        var phase = 0.0
        return ShortArray(samples) { i ->
            val t = i / 48_000.0
            if (i % 4_800 == 0) pitch = minPitch + rnd.nextDouble() * 110
            phase += 2 * PI * pitch / 48_000
            val gate = if (sin(2 * PI * 3.7 * t) > -0.2) 1.0 else 0.05
            var v = 0.0
            for (h in 1..12) v += sin(phase * h) / h
            (v * gate * 7_000 + rnd.nextDouble(-300.0, 300.0)).toInt().coerceIn(-32_000, 32_000).toShort()
        }
    }

    private fun mixedNote(beats: List<Long>, seed: Int, minPitch: Double = 110.0): FloatArray {
        val hb = HeartbeatSynth.render(beats, durationMs)
        val mixed = Mixer.mix(voice(hb.size, seed, minPitch), hb)
        return FloatArray(mixed.size) { mixed[it] / 32_768f }
    }

    /** "Lossy re-encode": 44.1 kHz, band-limited, with added noise. */
    private fun degrade(x48: FloatArray, seed: Int): FloatArray {
        val rnd = Random(seed)
        val x8 = BeatRecoveryCore.resampleTo8k(x48, 48_000) // band-limit + 8 kHz
        return FloatArray(x8.size) { x8[it] + (rnd.nextFloat() - 0.5f) * 0.02f }
    }

    private fun matchRate(truth: List<Long>, found: List<Long>): Double =
        truth.count { t -> found.any { abs(it - t) <= 60 } }.toDouble() / truth.size

    @Test
    fun recoversAtLeast90PercentOfBeatsThroughVoiceAndNoise() {
        // (BPM, seed, lowest voice pitch): 85 Hz = a deep male voice reaching into the band.
        for ((bpm, seed, pitch) in listOf(Triple(62.0, 1, 110.0), Triple(78.0, 2, 110.0), Triple(96.0, 3, 110.0), Triple(72.0, 4, 85.0))) {
            val truth = beats(bpm, seed)
            val pcm8k = degrade(mixedNote(truth, seed, pitch), seed)
            val result = BeatRecoveryCore.detect(pcm8k, durationMs)
            val rate = result?.let { matchRate(truth, it.beatsMs) } ?: 0.0
            val extra = (result?.beatsMs?.size ?: 0) - truth.size
            println("recovery ${bpm.toInt()} BPM, voice ≥${pitch.toInt()} Hz: ${"%.1f".format(rate * 100)}% matched within ±60 ms, ${extra} extra")
            assertTrue("match $rate at $bpm BPM", rate >= 0.9)
            assertTrue("extra beats $extra", extra <= truth.size / 10)
        }
    }

    @Test
    fun refusesAudioWithoutAHeartbeat() {
        for (pitch in listOf(110.0, 85.0)) {
            val v = voice(48_000 * 40, 9, pitch)
            val pcm8k = BeatRecoveryCore.resampleTo8k(FloatArray(v.size) { v[it] / 32_768f }, 48_000)
            assertNull("voice ≥$pitch Hz", BeatRecoveryCore.detect(pcm8k, durationMs))
        }
    }

    @Test
    fun recoversNotesMadeWithTheClassicSound() {
        val truth = beats(70.0, 5)
        val hb = HeartbeatSynth.render(truth, durationMs, HeartbeatSynth.Preset.CLASSIC)
        val mixed = Mixer.mix(voice(hb.size, 5), hb)
        val pcm8k = degrade(FloatArray(mixed.size) { mixed[it] / 32_768f }, 5)
        val rate = BeatRecoveryCore.detect(pcm8k, durationMs)?.let { matchRate(truth, it.beatsMs) } ?: 0.0
        println("recovery classic preset: ${"%.1f".format(rate * 100)}%")
        assertTrue("classic $rate", rate >= 0.9)
    }
}
