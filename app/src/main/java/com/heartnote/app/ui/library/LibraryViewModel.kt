package com.heartnote.app.ui.library

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heartnote.app.R
import com.heartnote.app.data.NoteMeta
import com.heartnote.app.data.NoteRepository
import com.heartnote.app.share.ImportManager
import com.heartnote.app.share.ShareExports
import com.heartnote.app.ui.playback.DotChartData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val loading: Boolean = true,
    val notes: List<NoteMeta> = emptyList(),
    /** 36 dot-chart columns of the latest note's beat envelope. */
    val latestColumns: FloatArray? = null,
    val importing: Boolean = false,
    @StringRes val message: Int? = null,
    /** Set after a successful import; the screen opens the note once. */
    val openNoteId: String? = null,
) {
    val latest: NoteMeta? get() = notes.firstOrNull()
    /** Notes below the card, grouped (SHARE_AND_IMPORT.md: "Yours" / "Received"). */
    val yours: List<NoteMeta> get() = notes.drop(1).filter { !it.isReceived }
    val received: List<NoteMeta> get() = notes.drop(1).filter { it.isReceived }
}

/**
 * F10 + F14. Notes only ever appear because the user recorded or imported
 * them (ONBOARDING.md R-O1: nothing is added without asking).
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NoteRepository(application.filesDir)
    private val importer = ImportManager(application, repository)
    private val exports = ShareExports(repository)
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    /** Called on every ON_START so renames and deletes from Playback show up. */
    fun refresh() {
        viewModelScope.launch {
            val (notes, columns) = withContext(Dispatchers.IO) {
                val notes = repository.list()
                notes to notes.firstOrNull()?.let { latest ->
                    runCatching { DotChartData.library(repository.readSignals(latest.id)) }.getOrNull()
                }
            }
            _uiState.update { it.copy(loading = false, notes = notes, latestColumns = columns) }
        }
    }

    /** Explicit user action only: picker, share target or "Open with". */
    fun import(uri: Uri) {
        if (_uiState.value.importing) return
        _uiState.update { it.copy(importing = true, message = null) }
        viewModelScope.launch {
            when (val result = importer.import(uri)) {
                is ImportManager.Result.Imported -> {
                    refresh()
                    _uiState.update { it.copy(importing = false, openNoteId = result.noteId) }
                }
                ImportManager.Result.NotAHeartnote ->
                    _uiState.update { it.copy(importing = false, message = R.string.import_not_heartnote) }
                ImportManager.Result.Failed ->
                    _uiState.update { it.copy(importing = false, message = R.string.import_failed) }
            }
        }
    }

    fun onNoteOpened() = _uiState.update { it.copy(openNoteId = null) }
    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    /** A note as a shareable file (own notes get the payload track). */
    suspend fun shareable(meta: NoteMeta): ShareExports.Shareable? =
        runCatching { exports.audio(meta) }.getOrNull()
}
