package com.heartnote.app.ui.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.heartnote.app.audio.HeartbeatSynth
import com.heartnote.app.ui.beat.BeatBus
import com.heartnote.app.ui.beat.BeatEvent
import com.heartnote.app.ui.beat.BeatKind
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

/**
 * Plays heartnote.m4a and emits each beat's LUB/DUB on [BeatBus] when the
 * player position crosses it (TRD § HAPTICS sync: 10 ms polling of
 * currentPosition). The heartbeat sound is already in the audio file, so the
 * events, haptics and visuals follow the same clock as what is heard.
 * Must be used from the main thread. Shared by PlaybackScreen and HoldScreen.
 */
class NotePlayer(context: Context, private val scope: CoroutineScope) {
    private data class Tick(val timeMs: Long, val kind: BeatKind, val beatMs: Long)

    private val player = ExoPlayer.Builder(context.applicationContext).build()
    private var ticks: List<Tick> = emptyList()
    private var cursor = 0
    private var lastPosition = 0L
    private var pollJob: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startPolling() else stopPolling()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _state.update { it.copy(durationMs = player.duration.coerceAtLeast(0)) }
                }
                if (playbackState == Player.STATE_ENDED) {
                    player.pause()
                    player.seekTo(0)
                    resetCursor(0)
                    _state.update { it.copy(positionMs = 0) }
                }
            }
        })
    }

    fun load(audio: File, beatsMs: List<Long>, durationMs: Long) {
        ticks = beatsMs.flatMapIndexed { i, beat ->
            listOf(
                Tick(beat, BeatKind.LUB, beat),
                Tick(beat + HeartbeatSynth.dubDelayMs(HeartbeatSynth.rrAt(beatsMs, i)), BeatKind.DUB, beat),
            )
        }.sortedBy { it.timeMs }
        resetCursor(0)
        _state.update { it.copy(durationMs = durationMs, positionMs = 0) }
        player.setMediaItem(MediaItem.fromUri(audio.toURI().toString()))
        player.prepare()
    }

    fun toggle() = if (player.isPlaying) pause() else play()

    fun play() {
        if (player.playbackState == Player.STATE_ENDED) seekTo(0)
        player.play()
    }

    fun pause() = player.pause()

    fun seekTo(ms: Long) {
        player.seekTo(ms)
        resetCursor(ms)
        _state.update { it.copy(positionMs = ms) }
    }

    fun release() {
        stopPolling()
        player.release()
    }

    private fun resetCursor(positionMs: Long) {
        cursor = ticks.indexOfFirst { it.timeMs > positionMs }.let { if (it < 0) ticks.size else it }
        lastPosition = positionMs
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                val position = player.currentPosition
                if (position < lastPosition) resetCursor(position) // seek or loop
                while (cursor < ticks.size && ticks[cursor].timeMs <= position) {
                    val tick = ticks[cursor++]
                    BeatBus.emit(BeatEvent(tick.kind, if (tick.kind == BeatKind.LUB) 1f else 0.5f, tick.beatMs))
                }
                lastPosition = position
                _state.update { it.copy(positionMs = position) }
                delay(POLL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        _state.update { it.copy(positionMs = player.currentPosition) }
    }

    companion object {
        private const val POLL_MS = 10L
    }
}
