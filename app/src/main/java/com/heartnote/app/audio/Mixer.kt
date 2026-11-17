package com.heartnote.app.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.tanh

/**
 * TRD § HEARTBEAT SYNTHESIS / Mixer: mixed = voice + heartbeat × 0.35, soft-clipped,
 * PCM16. The voice is first normalised (p99.9 → 0.7, at most +18 dB) because
 * phone mic levels vary widely and a quiet voice would be buried by the heartbeat.
 */
object Mixer {
    const val HEARTBEAT_GAIN = 0.35f
    private const val VOICE_TARGET = 0.7f
    private const val MAX_VOICE_GAIN = 8f
    private const val CLIP_KNEE = 0.9f
    private const val WAV_HEADER_SIZE = 44

    /** Reads PCM16 mono written by AudioCapture (fixed 44-byte header). */
    fun readWavPcm16(file: File): ShortArray {
        val bytes = file.readBytes()
        val count = (bytes.size - WAV_HEADER_SIZE).coerceAtLeast(0) / 2
        val out = ShortArray(count)
        ByteBuffer.wrap(bytes, WAV_HEADER_SIZE, count * 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
        return out
    }

    fun voiceGain(voice: ShortArray): Float {
        if (voice.isEmpty()) return 1f
        val histogram = IntArray(32_769)
        for (s in voice) histogram[min(abs(s.toInt()), 32_768)]++
        val target = (voice.size * 0.999).toLong()
        var seen = 0L
        var level = 0
        while (level < histogram.size - 1 && seen + histogram[level] <= target) {
            seen += histogram[level]
            level++
        }
        if (level == 0) return 1f // silent input: leave as is
        return (VOICE_TARGET * 32_768f / level).coerceIn(1f, MAX_VOICE_GAIN)
    }

    fun mix(voice: ShortArray, heartbeat: FloatArray): ShortArray {
        val gain = voiceGain(voice)
        return ShortArray(voice.size) { i ->
            val v = voice[i] / 32_768f * gain
            val h = if (i < heartbeat.size) heartbeat[i] * HEARTBEAT_GAIN else 0f
            (softClip(v + h) * 32_767f).toInt().toShort()
        }
    }

    /** Linear below the knee, tanh above, never exceeding ±1. */
    fun softClip(x: Float): Float {
        val a = abs(x)
        if (a <= CLIP_KNEE) return x
        val room = 1f - CLIP_KNEE
        return sign(x) * (CLIP_KNEE + room * tanh((a - CLIP_KNEE) / room))
    }
}
