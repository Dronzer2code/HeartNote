package com.heartnote.app.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.tanh

/** Voice and heartbeat gains in dB (Fix 5). Defaults until tuned by ear on the phone speaker. */
data class MixSettings(val voiceGainDb: Float = DEFAULT_VOICE_DB, val heartbeatGainDb: Float = DEFAULT_HEARTBEAT_DB) {
    companion object {
        const val DEFAULT_VOICE_DB = -9f
        const val DEFAULT_HEARTBEAT_DB = 0f
        val VOICE_RANGE = -18f..0f
        val HEARTBEAT_RANGE = -12f..6f

        /** Used by every new mix (in-app playback = the .m4a = the .mp4 audio). Debug tuning may override. */
        @Volatile
        var current = MixSettings()
    }
}

/**
 * Loudness mix (2026-11-18, Fix 5): the heartbeat is the dominant sound, the
 * voice stays clear but quieter.
 * 1. Voice normalised to −24 dBFS RMS (gated: only 50 ms frames above −50 dBFS
 *    count, so pauses don't inflate the gain; max +30 dB; silence untouched),
 *    then × voice gain.
 * 2. Heartbeat (SPEAKER preset, peak 1.0) scaled to peak −3 dBFS, × heartbeat gain.
 * 3. Sum through a peak limiter at −1 dBFS (instant attack, 50 ms release), then
 *    a soft clip at the same ceiling.
 */
object Mixer {
    const val VOICE_RMS_DBFS = -24f
    const val HEARTBEAT_PEAK_DBFS = -3f
    const val LIMIT_DBFS = -1f
    private const val GATE_DBFS = -50f
    private const val MAX_NORMALISE_DB = 30f
    private const val FRAME_MS = 50
    private const val RELEASE_MS = 50.0
    private const val WAV_HEADER_SIZE = 44

    private val limit = dbToLinear(LIMIT_DBFS)

    /** Reads PCM16 mono written by AudioCapture (fixed 44-byte header). */
    fun readWavPcm16(file: File): ShortArray {
        val bytes = file.readBytes()
        val count = (bytes.size - WAV_HEADER_SIZE).coerceAtLeast(0) / 2
        val out = ShortArray(count)
        ByteBuffer.wrap(bytes, WAV_HEADER_SIZE, count * 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
        return out
    }

    /** Gated RMS of [voice] in dBFS, or null when there is no speech above the gate. */
    fun gatedRmsDbfs(voice: ShortArray, sampleRate: Int = 48_000): Float? {
        val frame = sampleRate * FRAME_MS / 1000
        val gate = dbToLinear(GATE_DBFS).toDouble()
        var sum = 0.0
        var count = 0L
        var i = 0
        while (i + frame <= voice.size) {
            var e = 0.0
            for (k in i until i + frame) {
                val s = voice[k] / 32_768.0
                e += s * s
            }
            if (sqrt(e / frame) > gate) {
                sum += e
                count += frame
            }
            i += frame
        }
        if (count == 0L) return null
        return (20 * log10(sqrt(sum / count))).toFloat()
    }

    /** Linear gain that brings the voice to [VOICE_RMS_DBFS] and applies the voice gain. */
    fun voiceGain(voice: ShortArray, settings: MixSettings = MixSettings.current): Float {
        val rms = gatedRmsDbfs(voice) ?: return 1f // silent input: leave as is
        val normalise = (VOICE_RMS_DBFS - rms).coerceAtMost(MAX_NORMALISE_DB)
        return dbToLinear(normalise + settings.voiceGainDb)
    }

    fun heartbeatGain(settings: MixSettings = MixSettings.current): Float =
        dbToLinear(HEARTBEAT_PEAK_DBFS + settings.heartbeatGainDb)

    fun mix(
        voice: ShortArray,
        heartbeat: FloatArray,
        settings: MixSettings = MixSettings.current,
        sampleRate: Int = 48_000,
    ): ShortArray {
        val vg = voiceGain(voice, settings)
        val hg = heartbeatGain(settings)
        val release = (1.0 - exp(-1.0 / (RELEASE_MS / 1000.0 * sampleRate))).toFloat()
        var g = 1f
        return ShortArray(voice.size) { i ->
            val x = voice[i] / 32_768f * vg + (if (i < heartbeat.size) heartbeat[i] * hg else 0f)
            val target = if (abs(x) > limit) limit / abs(x) else 1f
            g = if (target < g) target else g + (1f - g) * release
            (softClip(x * g, limit) * 32_767f).toInt().toShort()
        }
    }

    /** Linear below 90% of [ceiling], tanh above, never exceeding ±[ceiling]. */
    fun softClip(x: Float, ceiling: Float = 1f): Float {
        val knee = 0.9f * ceiling
        val a = abs(x)
        if (a <= knee) return x
        val room = ceiling - knee
        return sign(x) * (knee + room * tanh((a - knee) / room))
    }

    fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)
}
