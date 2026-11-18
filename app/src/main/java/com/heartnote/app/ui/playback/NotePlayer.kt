package com.heartnote.app.ui.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.heartnote.app.haptics.HapticsLog
import com.heartnote.app.ui.beat.BeatBus
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
    private val player = ExoPlayer.Builder(context.applicationContext).build()
    private var cursor = BeatCursor(emptyList())
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
                    cursor.reset(0)
                    _state.update { it.copy(positionMs = 0) }
                }
            }
        })
    }

    fun load(audio: File, beatsMs: List<Long>, durationMs: Long) {
        cursor = BeatCursor(beatsMs)
        cursor.reset(0)
        HapticsLog.d { "player load ${audio.name} exists=${audio.exists()} beats=${cursor.beatCount} durationMs=$durationMs" }
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
        cursor.reset(ms)
        _state.update { it.copy(positionMs = ms) }
    }

    fun release() {
        stopPolling()
        player.release()
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                val position = player.currentPosition
                cursor.advance(position) { event ->
                    HapticsLog.d { "fire ${event.kind} beat=${event.noteTimeMs} pos=$position" }
                    BeatBus.emit(event)
                }
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
