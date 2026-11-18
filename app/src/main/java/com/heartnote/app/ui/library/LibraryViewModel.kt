package com.heartnote.app.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heartnote.app.data.NoteMeta
import com.heartnote.app.data.NoteRepository
import com.heartnote.app.ui.playback.DotChartData
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val loading: Boolean = true,
    val notes: List<NoteMeta> = emptyList(),
    /** 36 dot-chart columns of the latest note's beat envelope. */
    val latestColumns: FloatArray? = null,
) {
    val latest: NoteMeta? get() = notes.firstOrNull()
    val older: List<NoteMeta> get() = notes.drop(1)
}

/**
 * F10: lists finished notes. Notes only ever appear because the user recorded
 * or imported them (ONBOARDING.md R-O1: nothing is added without asking).
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NoteRepository(application.filesDir)
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    /** Called on every ON_START so renames and deletes from Playback show up. */
    fun refresh() {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                val notes = repository.list()
                val columns = notes.firstOrNull()?.let { latest ->
                    runCatching { DotChartData.library(repository.readSignals(latest.id)) }.getOrNull()
                }
                LibraryUiState(loading = false, notes = notes, latestColumns = columns)
            }
            _uiState.value = state
        }
    }

    fun audioFile(id: String): File = repository.audioFile(id)
}
