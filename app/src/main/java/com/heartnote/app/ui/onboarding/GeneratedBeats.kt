package com.heartnote.app.ui.onboarding

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.heartnote.app.audio.HeartbeatSynth
import com.heartnote.app.audio.Mixer
import com.heartnote.app.ui.beat.BeatEvent
import com.heartnote.app.ui.beat.BeatKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ONBOARDING.md R-O7: tour beats are generated, never a real person's.
 * Events carry no note time, so they never reach the playback haptics path.
 */
object GeneratedBeats {
    const val BPM = 70
    val rrMs: Long = 60_000L / BPM

    /** Endless lub/dub at 70 BPM for the tour visuals. */
    fun flow(): Flow<BeatEvent> = flow {
        val dub = HeartbeatSynth.dubDelayMs(rrMs)
        while (true) {
            emit(BeatEvent(BeatKind.LUB, 1f, null))
            delay(dub)
            emit(BeatEvent(BeatKind.DUB, 0.5f, null))
            delay(rrMs - dub)
        }
    }

    /** Beat times for [durationMs] of generated heartbeat, first beat after 300 ms. */
    fun beatsMs(durationMs: Long): List<Long> = (300L until durationMs step rrMs).toList()
}

/**
 * "Feel a sample beat": 8 s of HeartbeatSynth at 70 BPM, no voice, played
 * from memory (nothing is saved). Beat events follow the AudioTrack playback
 * head so sound, vibration and the heart stay together.
 */
class SampleBeatPlayer(private val scope: CoroutineScope, private val onBeat: (BeatKind) -> Unit) {
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing
    private val _events = MutableSharedFlow<BeatEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BeatEvent> = _events

    private var track: AudioTrack? = null
    private var job: Job? = null

    fun toggle() = if (_playing.value) stop() else start()

    fun start() {
        if (_playing.value) return
        _playing.value = true
        job = scope.launch {
            val beats = GeneratedBeats.beatsMs(DURATION_MS)
            val pcm = withContext(Dispatchers.Default) {
                val hb = HeartbeatSynth.render(beats, DURATION_MS)
                ShortArray(hb.size) { (Mixer.softClip(hb[it] * SAMPLE_GAIN) * 32_767f).toInt().toShort() }
            }
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(HeartbeatSynth.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track = t
            try {
                t.write(pcm, 0, pcm.size)
                t.play()
                val ticks = beats.flatMap { b ->
                    listOf(b to BeatKind.LUB, b + HeartbeatSynth.dubDelayMs(GeneratedBeats.rrMs) to BeatKind.DUB)
                }.sortedBy { it.first }
                var cursor = 0
                while (isActive && cursor < ticks.size) {
                    val posMs = t.playbackHeadPosition * 1000L / HeartbeatSynth.SAMPLE_RATE
                    while (cursor < ticks.size && ticks[cursor].first <= posMs) {
                        val kind = ticks[cursor++].second
                        onBeat(kind)
                        _events.tryEmit(BeatEvent(kind, if (kind == BeatKind.LUB) 1f else 0.5f, null))
                    }
                    delay(10)
                }
                delay(300) // let the last dub ring out
            } finally {
                release()
            }
        }
    }

    fun stop() {
        job?.cancel()
        release()
    }

    private fun release() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
        _playing.value = false
    }

    companion object {
        const val DURATION_MS = 8_000L
        private const val SAMPLE_GAIN = 0.8f
    }
}
