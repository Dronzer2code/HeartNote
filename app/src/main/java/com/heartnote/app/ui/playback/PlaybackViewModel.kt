package com.heartnote.app.ui.playback

import android.app.Application
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.heartnote.app.data.NoteMeta
import com.heartnote.app.data.NoteRepository
import com.heartnote.app.data.SignalsFile
import com.heartnote.app.haptics.HeartbeatHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaybackUiState(
    val meta: NoteMeta? = null,
    val signals: SignalsFile? = null,
    val player: PlayerState = PlayerState(),
    val vibrationAvailable: Boolean = true,
    val bluetoothOutput: Boolean = false,
    val deleted: Boolean = false,
    val loadFailed: Boolean = false,
)

/** F7 playback (also used by HoldScreen via the same NotePlayer). */
open class PlaybackViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    val noteId: String = checkNotNull(savedStateHandle[NOTE_ID_ARG])

    private val repository = NoteRepository(application.filesDir)
    private val player = NotePlayer(application, viewModelScope)
    private val audioManager = application.getSystemService(AudioManager::class.java)

    private val _uiState = MutableStateFlow(
        PlaybackUiState(vibrationAvailable = HeartbeatHaptics(application).isAvailable()),
    )
    val uiState: StateFlow<PlaybackUiState> = _uiState

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshOutput()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshOutput()
    }

    init {
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
        refreshOutput()
        viewModelScope.launch {
            player.state.collect { p -> _uiState.update { it.copy(player = p) } }
        }
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { repository.readMeta(noteId) to repository.readSignals(noteId) }.getOrNull()
            }
            val meta = loaded?.first
            val signals = loaded?.second
            if (meta == null || signals == null || !repository.audioFile(noteId).exists()) {
                _uiState.update { it.copy(loadFailed = true) }
                return@launch
            }
            _uiState.update { it.copy(meta = meta, signals = signals) }
            player.load(repository.audioFile(noteId), signals.beatsMs, signals.durationMs)
        }
    }

    fun togglePlay() = player.toggle()
    fun play() = player.play()
    fun pause() = player.pause()
    fun seekTo(ms: Long) = player.seekTo(ms)

    fun rename(title: String) {
        viewModelScope.launch {
            val renamed = withContext(Dispatchers.IO) { repository.rename(noteId, title) }
            if (renamed != null) _uiState.update { it.copy(meta = renamed) }
        }
    }

    fun delete() {
        player.pause()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.delete(noteId) }
            _uiState.update { it.copy(deleted = true) }
        }
    }

    fun audioFile() = repository.audioFile(noteId)

    private fun refreshOutput() {
        val bluetooth = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in bluetoothTypes }
        _uiState.update { it.copy(bluetoothOutput = bluetooth) }
    }

    override fun onCleared() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        player.release()
    }

    companion object {
        const val NOTE_ID_ARG = "noteId"

        private val bluetoothTypes = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(AudioDeviceInfo.TYPE_BLE_BROADCAST)
        }
    }
}
