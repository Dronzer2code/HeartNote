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
    fun quietVoiceIsBoostedSilentVoiceIsNot() {
        val quiet = ShortArray(48_000) { (if (it % 2 == 0) 2_000 else -2_000).toShort() }
        assertEquals(8f, Mixer.voiceGain(quiet), 0.01f) // capped at +18 dB
        assertEquals(1f, Mixer.voiceGain(ShortArray(48_000)), 0f)
    }

    @Test
    fun softClipIsLinearBelowKneeAndBounded() {
        assertEquals(0.5f, Mixer.softClip(0.5f), 0f)
        assertTrue(Mixer.softClip(5f) <= 1f)
        assertTrue(Mixer.softClip(-5f) >= -1f)
    }
}
