package com.heartnote.app.audio

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPipelineTest {

    @Test
    fun synthPlacesLubAtBeatAndDubAfterClampedDelay() {
        val beats = listOf(1_000L, 1_800L)
        val pcm = HeartbeatSynth.render(beats, 3_000)
        assertEquals(HeartbeatSynth.msToSamples(3_000.0), pcm.size)

        fun energy(fromMs: Long, toMs: Long) =
            (HeartbeatSynth.msToSamples(fromMs.toDouble()) until HeartbeatSynth.msToSamples(toMs.toDouble()))
                .sumOf { (pcm[it] * pcm[it]).toDouble() }

        assertTrue(energy(0, 990) < 1e-9)                // silence before the first beat
        assertTrue(energy(1_000, 1_120) > 1.0)           // lub
        val dubDelay = HeartbeatSynth.dubDelayMs(800)    // 0.3 × 800 = 240 ms
        assertEquals(240L, dubDelay)
        assertTrue(energy(1_240, 1_330) > 0.1)           // dub
        assertTrue(energy(1_400, 1_790) < 1e-3)          // quiet until the next beat
    }

    @Test
    fun speakerPresetHasAnAudibleTransientClassicDoesNot() {
        fun energyAt(pcm: FloatArray, hz: Double, from: Int, samples: Int): Double {
            // Goertzel power over pcm[from, from + samples).
            val k = 2 * Math.cos(2 * Math.PI * hz / HeartbeatSynth.SAMPLE_RATE)
            var s1 = 0.0
            var s2 = 0.0
            for (i in from until from + samples) {
                val s0 = pcm[i] + k * s1 - s2
                s2 = s1
                s1 = s0
            }
            return s1 * s1 + s2 * s2 - k * s1 * s2
        }
        val n = HeartbeatSynth.msToSamples(8.0)
        val speaker = HeartbeatSynth.lubTemplate(HeartbeatSynth.Preset.SPEAKER)
        val classic = HeartbeatSynth.lubTemplate(HeartbeatSynth.Preset.CLASSIC)
        // The 800 Hz knock (gain 0.15) in the first 8 ms. Measured ≈ 3.7× CLASSIC; over such a
        // short window the low harmonics' spectral leakage is of the same order, hence 2×.
        assertTrue(energyAt(speaker, 800.0, 0, n) > 2 * energyAt(classic, 800.0, 0, n))
        // Harmonics present (2nd at 0.5 amplitude ≈ 0.25 power of the fundamental).
        assertTrue(energyAt(speaker, 110.0, 0, speaker.size) > 0.1 * energyAt(speaker, 55.0, 0, speaker.size))
    }

    @Test
    fun limiterKeepsTheSumUnderMinus1Dbfs() {
        val voice = ShortArray(48_000) { (kotlin.math.sin(it / 20.0) * 30_000).toInt().toShort() }
        val heartbeat = HeartbeatSynth.render(listOf(100, 900), 1_000)
        val mixed = Mixer.mix(voice, heartbeat, MixSettings(voiceGainDb = 0f, heartbeatGainDb = 6f))
        val ceiling = (Mixer.dbToLinear(Mixer.LIMIT_DBFS) * 32_767).toInt() + 1
        assertTrue(mixed.all { abs(it.toInt()) <= ceiling })
    }

    @Test
    fun voiceIsNormalisedToMinus24DbfsRmsThenGained() {
        for (amplitude in listOf(800.0, 3_000.0, 20_000.0)) {
            val voice = ShortArray(48_000 * 2) { (kotlin.math.sin(it * 2 * Math.PI * 180 / 48_000) * amplitude).toInt().toShort() }
            val out = Mixer.mix(voice, FloatArray(0), MixSettings(voiceGainDb = -9f))
            val rms = Mixer.gatedRmsDbfs(out)!!
            assertEquals("amplitude $amplitude", -33f, rms, 0.5f) // −24 dBFS − 9 dB
        }
    }

    @Test
    fun heartbeatPeaksAtMinus3Dbfs() {
        val hb = HeartbeatSynth.render(listOf(100), 1_000)
        val out = Mixer.mix(ShortArray(48_000), hb, MixSettings(heartbeatGainDb = 0f))
        val peakDb = 20 * kotlin.math.log10(out.maxOf { abs(it.toInt()) } / 32_767.0)
        assertEquals(-3.0, peakDb, 0.3)
    }

    @Test
    fun dubDelayIsClamped() {
        assertEquals(200L, HeartbeatSynth.dubDelayMs(400))
        assertEquals(350L, HeartbeatSynth.dubDelayMs(1_400))
    }

    @Test
    fun mixKeepsVoiceLengthAndNeverClips() {
        val voice = ShortArray(48_000) { (if (it % 2 == 0) 30_000 else -30_000).toShort() }
        val heartbeat = FloatArray(10_000) { 1f }
        val mixed = Mixer.mix(voice, heartbeat)
        assertEquals(voice.size, mixed.size)
        assertTrue(mixed.all { abs(it.toInt()) <= 32_767 })
    }

    @Test
    fun silentVoiceIsLeftAlone() {
        assertEquals(1f, Mixer.voiceGain(ShortArray(48_000)), 0f)
    }

    @Test
    fun softClipIsLinearBelowKneeAndBounded() {
        assertEquals(0.5f, Mixer.softClip(0.5f), 0f)
        assertTrue(Mixer.softClip(5f) <= 1f)
        assertTrue(Mixer.softClip(-5f) >= -1f)
    }
}
