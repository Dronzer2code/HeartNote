package com.heartnote.app.ui.share

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heartnote.app.data.NoteMeta
import com.heartnote.app.data.NoteRepository
import com.heartnote.app.R
import com.heartnote.app.share.DeviceSaver
import com.heartnote.app.share.ShareExports
import com.heartnote.app.ui.playback.VideoExportState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareUiState(
    /** The note the sheet is open for; null = sheet closed. */
    val meta: NoteMeta? = null,
    val video: VideoExportState = VideoExportState.Idle,
    val busy: Boolean = false,
    /** A file ready for the system share sheet; the host shares it once. */
    val shareReady: ShareExports.Shareable? = null,
    /** Display name of a file just saved to Downloads/Heartnote (shows the instructions). */
    val savedName: String? = null,
    @androidx.annotation.StringRes val error: Int? = null,
)

/**
 * The single share flow for every screen (Playback, Hold, Library "share
 * latest"). Builds the file to share; [NoteShareSheet] hands it to
 * ShareHelper, the only place that creates a share Intent.
 */
class ShareViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NoteRepository(application.filesDir)
    private val exports = ShareExports(repository, application)
    private var videoJob: Job? = null

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState

    fun open(meta: NoteMeta) {
        if (videoJob?.isActive == true) return
        _uiState.value = ShareUiState(meta = meta)
    }

    fun dismiss() {
        if (videoJob?.isActive == true) return
        _uiState.value = ShareUiState()
    }

    /** F13 video (rendered once, cached until rename). Cancellable. */
    fun shareVideo() = withVideo { file -> _uiState.update { it.copy(shareReady = file) } }

    fun shareAudio() = withAudio { file -> _uiState.update { it.copy(shareReady = file) } }

    /** "Save video to phone": the exported .mp4 with payload → Downloads/Heartnote. */
    fun saveVideo() = withVideo { save(it) }

    /** "Save audio to phone" (or the original file of a received note). */
    fun saveAudio() = withAudio { save(it) }

    fun onSaveDenied() = _uiState.update { it.copy(error = R.string.save_permission_denied) }

    fun closeSaved() = _uiState.update { ShareUiState() }

    private fun save(file: ShareExports.Shareable) {
        val meta = _uiState.value.meta ?: return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val name = DeviceSaver.fileName(meta.title, file.file.extension)
            runCatching { DeviceSaver.save(getApplication(), file.file, name, file.mimeType) }
                .onSuccess { saved -> _uiState.update { it.copy(busy = false, savedName = saved) } }
                .onFailure { e ->
                    Log.e(TAG, "save to Downloads failed", e)
                    _uiState.update { it.copy(busy = false, error = R.string.save_failed) }
                }
        }
    }

    fun cancelVideo() {
        videoJob?.cancel()
        _uiState.update { it.copy(video = VideoExportState.Idle) }
    }

    /** Called by the host after ShareHelper opened the chooser. */
    fun onShared() = _uiState.update { ShareUiState() }

    internal fun withVideo(then: (ShareExports.Shareable) -> Unit) {
        val meta = _uiState.value.meta ?: return
        if (videoJob?.isActive == true) return
        _uiState.update { it.copy(video = VideoExportState.Running(0f)) }
        videoJob = viewModelScope.launch {
            val result = runCatching {
                exports.video(meta) { p -> _uiState.update { it.copy(video = VideoExportState.Running(p)) } }
            }
            val error = result.exceptionOrNull()
            if (error is CancellationException) {
                _uiState.update { it.copy(video = VideoExportState.Idle) }
                throw error
            }
            if (error == null) {
                _uiState.update { it.copy(video = VideoExportState.Idle) }
                then(result.getOrThrow())
            } else {
                Log.e(TAG, "video export failed", error)
                _uiState.update { it.copy(video = VideoExportState.Failed) }
            }
        }
    }

    /** Audio with payload (own notes) or the original received file. */
    internal fun withAudio(then: (ShareExports.Shareable) -> Unit) {
        val meta = _uiState.value.meta ?: return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val file = runCatching { exports.audio(meta) }
                .onFailure { Log.e(TAG, "audio export failed", it) }
                .getOrNull()
            _uiState.update { it.copy(busy = false) }
            file?.let(then)
        }
    }

    companion object {
        private const val TAG = "Heartnote"
    }
}
