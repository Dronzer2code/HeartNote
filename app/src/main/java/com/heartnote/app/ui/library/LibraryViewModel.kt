package com.heartnote.app.ui.library

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.heartnote.app.R
import com.heartnote.app.data.NoteProcessor
import androidx.lifecycle.viewModelScope
import com.heartnote.app.data.NoteMeta
import com.heartnote.app.data.NoteRepository
import com.heartnote.app.signal.TimedValue
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

/** F10: lists finished notes; the latest note's signal track feeds its dot chart. */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NoteRepository(application.filesDir)
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    /** Called on every ON_START so renames and deletes from Playback show up. */
    fun refresh() {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                finishUnfinishedNotes()
                val notes = repository.list()
                val columns = notes.firstOrNull()?.let { latest ->
                    runCatching {
                        val signals = repository.readSignals(latest.id)
                        DotChartData.library(signals.raw.bpTrace.map { TimedValue(it.tMs, it.v) }, signals.durationMs)
                    }.getOrNull()
                }
                LibraryUiState(loading = false, notes = notes, latestColumns = columns)
            }
            _uiState.value = state
        }
    }

    fun audioFile(id: String): File = repository.audioFile(id)

    /**
     * Completes notes left with raw files only. The Library is never started
     * while RecordScreen is processing, so this cannot race the live pipeline.
     * One attempt per note per process, so a failing note cannot loop.
     */
    private suspend fun finishUnfinishedNotes() {
        val processor = NoteProcessor(repository)
        val title = getApplication<Application>().getString(R.string.default_note_title)
        for (id in repository.unfinished()) {
            if (!attempted.add(id)) continue
            runCatching {
                val createdAt = repository.readSignals(id).audioStartEpochMs
                if (repository.readMeta(id) == null) processor.process(id, title, createdAt) else processor.render(id)
            }.onFailure { Log.w(TAG, "could not finish note $id", it) }
        }
    }

    companion object {
        private const val TAG = "Heartnote"
        private val attempted = java.util.Collections.synchronizedSet(HashSet<String>())
    }
}
