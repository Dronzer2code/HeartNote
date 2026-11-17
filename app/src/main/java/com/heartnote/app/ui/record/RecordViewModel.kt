package com.heartnote.app.ui.record

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.heartnote.app.R
import com.heartnote.app.capture.RecordingSession
import com.heartnote.app.capture.SignalCapture
import com.heartnote.app.data.NoteProcessor
import com.heartnote.app.data.NoteRepository
import com.heartnote.app.signal.trailingMedian
import com.heartnote.app.ui.beat.LiveBeatScheduler
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraError
import com.presagetech.smartspectra.ValidationStatus
import com.presagetech.smartspectra.proto.MetricsProto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** PROCESS_FLOW § RECORD SCREEN STATE MACHINE + DESIGN.md RecordScreen state table. */
enum class RecordPhase { CONFIRM, PREVIEW, LOCK_ON, RECORDING, FLUSHING, PROCESSING, FAILED, SAVED }

/** Why the screen returned to Preview (shown as the secondary status line). */
enum class RecordInfo(@StringRes val text: Int) {
    LOCK_ON_TIMEOUT(R.string.record_info_lockon_timeout),
    FACE_LOST(R.string.record_info_face_lost),
    MIC_SILENT(R.string.record_error_mic_silent),
    MIC_ERROR(R.string.record_info_mic_error),
    BACKGROUNDED(R.string.record_info_background),
    SAVE_FAILED(R.string.record_info_save_failed),
}

data class RecordUiState(
    val phase: RecordPhase = RecordPhase.CONFIRM,
    val sdkStatus: ProcessingStatus = ProcessingStatus.IDLE,
    val facePresent: Boolean = false,
    val pulseBpm: Int? = null,                      // 5-sample trailing median (S5)
    val breathingWave: List<Float> = emptyList(),   // recent brUpperTrace values
    val lockOnFraction: Float = 0f,                 // 0–1 over the 20 s timeout; 1 at first pulse
    val recordedMs: Long = 0,
    val validationHint: String? = null,
    val sdkError: String? = null,
    val info: RecordInfo? = null,
    val savedNoteId: String? = null,
)

class RecordViewModel(application: Application) : AndroidViewModel(application) {
    private val capture = SignalCapture()
    private val repository = NoteRepository(application.filesDir)
    private val processor = NoteProcessor(repository)
    private val liveBeats = LiveBeatScheduler(viewModelScope)

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState

    /**
     * Latest brUpperTrace value normalised to −1..1 over the visible window.
     * Read every frame by the sphere; kept out of the StateFlow so 30 Hz
     * breathing samples do not recompose the screen.
     */
    @Volatile
    var breathNormalised: Float = 0f
        private set

    private var session: RecordingSession? = null

    // All times below are SystemClock.elapsedRealtime() ms.
    private var lastFaceAtMs = Long.MIN_VALUE / 2
    private var lockOnStartedAtMs = 0L

    private val pulseWindow = ArrayDeque<Float>()
    private var lastPulseUs = Long.MIN_VALUE
    private val waveBuffer = ArrayDeque<Float>()
    private var lastWaveUs = Long.MIN_VALUE
    private var lastTraceUs = Long.MIN_VALUE

    private val statusObserver = Observer<ProcessingStatus> { status ->
        _uiState.update { it.copy(sdkStatus = status) }
    }
    private val validationObserver = Observer<ValidationStatus?> { validation ->
        _uiState.update { it.copy(validationHint = validation?.hint?.takeIf { h -> h.isNotBlank() }) }
    }
    private val errorObserver = Observer<SmartSpectraError?> { error ->
        if (error != null) {
            _uiState.update { it.copy(sdkError = error.message) }
            val phase = _uiState.value.phase
            if (phase == RecordPhase.LOCK_ON || phase == RecordPhase.RECORDING) discardSession(null)
        }
    }

    init {
        capture.processingStatus.observeForever(statusObserver)
        capture.validationStatus.observeForever(validationObserver)
        capture.error.observeForever(errorObserver)
        viewModelScope.launch { capture.metricsFlow.collect { onMetrics(it) } }
        startTicker()
    }

    private fun onMetrics(metrics: MetricsProto.Metrics) {
        // Emissions are split by type: a face-bearing emission refreshes presence.
        if (metrics.hasFace()) {
            lastFaceAtMs = SystemClock.elapsedRealtime()
            if (!_uiState.value.facePresent) _uiState.update { it.copy(facePresent = true) }
        }

        session?.let { s ->
            val phase = _uiState.value.phase
            if (phase == RecordPhase.LOCK_ON || phase == RecordPhase.RECORDING || phase == RecordPhase.FLUSHING) {
                s.onMetrics(metrics)
            }
        }

        if (metrics.hasCardio()) {
            val newTrace = metrics.cardio.arterialPressureTraceList.filter { it.timestamp > lastTraceUs }
            if (newTrace.isNotEmpty()) {
                lastTraceUs = newTrace.last().timestamp
                liveBeats.onTrace(newTrace.map { it.timestamp to it.value })
            }
            val newPulse = metrics.cardio.pulseRateList.filter { it.timestamp > lastPulseUs }
            if (newPulse.isNotEmpty()) {
                lastPulseUs = newPulse.last().timestamp
                liveBeats.onPulseRate(newPulse.map { it.timestamp to it.value })
                newPulse.forEach {
                    pulseWindow.addLast(it.value)
                    while (pulseWindow.size > PULSE_MEDIAN_WINDOW) pulseWindow.removeFirst()
                }
                _uiState.update { it.copy(pulseBpm = trailingMedian(pulseWindow, PULSE_MEDIAN_WINDOW)?.roundToInt()) }
                // F3: lock-on completes on the first pulseRate sample after LockOn entry.
                if (_uiState.value.phase == RecordPhase.LOCK_ON) beginRecording()
            }
        }

        if (metrics.hasBreathing()) {
            val newWave = metrics.breathing.upperTraceList.filter { it.timestamp > lastWaveUs }
            if (newWave.isNotEmpty()) {
                lastWaveUs = newWave.last().timestamp
                newWave.forEach {
                    waveBuffer.addLast(it.value)
                    while (waveBuffer.size > WAVE_WINDOW) waveBuffer.removeFirst()
                }
                val lo = waveBuffer.min()
                val hi = waveBuffer.max()
                breathNormalised = if (hi - lo > 1e-6f) ((waveBuffer.last() - lo) / (hi - lo)) * 2f - 1f else 0f
                _uiState.update { it.copy(breathingWave = waveBuffer.toList()) }
            }
        }
    }

    fun attachPreview(surfaceProvider: Preview.SurfaceProvider) {
        capture.attachPreview(surfaceProvider)
    }

    /** Called when camera + mic permissions are granted; the SDK runs from Confirm on. */
    fun startPreview() {
        viewModelScope.launch {
            // A previous session may still be STOPPING; wait briefly for IDLE.
            var waited = 0L
            while (_uiState.value.sdkStatus == ProcessingStatus.STOPPING && waited < 5000) {
                delay(100)
                waited += 100
            }
            val status = _uiState.value.sdkStatus
            if (status != ProcessingStatus.IDLE && status != ProcessingStatus.ERROR) return@launch
            _uiState.update { it.copy(sdkError = null) }
            liveBeats.reset()
            runCatching { capture.start() }
                .onFailure { e ->
                    Log.e(TAG, "sdk.start() failed", e)
                    _uiState.update { it.copy(sdkError = e.message) }
                }
        }
    }

    /** F2: Confirm ↔ Preview with the "I am recording myself" checkbox. */
    fun setRecordingMyself(confirmed: Boolean) {
        val phase = _uiState.value.phase
        if (confirmed && phase == RecordPhase.CONFIRM) {
            _uiState.update { it.copy(phase = RecordPhase.PREVIEW) }
        } else if (!confirmed && phase == RecordPhase.PREVIEW) {
            _uiState.update { it.copy(phase = RecordPhase.CONFIRM) }
        }
    }

    /** Pill action: Start in Preview, Stop (cancel) in LockOn, Stop in Recording. */
    fun onPillAction() {
        when (_uiState.value.phase) {
            RecordPhase.PREVIEW -> tapStart()
            RecordPhase.LOCK_ON -> discardSession(null)
            RecordPhase.RECORDING -> finishRecording()
            else -> Unit
        }
    }

    /** Preview → LockOn. Requires a face emission within the last 1000 ms. */
    private fun tapStart() {
        if (!_uiState.value.facePresent) return
        session = RecordingSession(repository)
        lockOnStartedAtMs = SystemClock.elapsedRealtime()
        _uiState.update {
            it.copy(phase = RecordPhase.LOCK_ON, info = null, sdkError = null, lockOnFraction = 0f, recordedMs = 0)
        }
    }

    /** LockOn → Recording: audio starts here; lock-on silence is not in the note. */
    private fun beginRecording() {
        val s = session ?: return
        _uiState.update { it.copy(phase = RecordPhase.RECORDING, lockOnFraction = 1f, recordedMs = 0) }
        s.startAudio(viewModelScope) { e ->
            Log.e(TAG, "audio capture failed", e)
            viewModelScope.launch { discardSession(RecordInfo.MIC_ERROR) }
        }
    }

    /** Recording → Flushing (2.5 s SDK tail flush) → Processing. */
    private fun finishRecording() {
        val s = session ?: return
        if (_uiState.value.phase != RecordPhase.RECORDING) return
        _uiState.update { it.copy(phase = RecordPhase.FLUSHING) }
        viewModelScope.launch {
            val durationMs = s.stopAudio()
            delay(SDK_TAIL_FLUSH_MS) // metrics keep flowing into the session meanwhile
            runCatching { capture.stop() }
            runCatching { withContext(Dispatchers.IO) { s.writeSignals(durationMs) } }
                .onSuccess { processNote(s) }
                .onFailure { e ->
                    Log.e(TAG, "saving signals failed", e)
                    s.discard()
                    session = null
                    _uiState.update { it.copy(phase = RecordPhase.CONFIRM, info = RecordInfo.SAVE_FAILED) }
                    startPreview()
                }
        }
    }

    /** Processing (F6 pipeline) → Saved, or Failed with Retry/Delete. */
    private suspend fun processNote(s: RecordingSession) {
        _uiState.update { it.copy(phase = RecordPhase.PROCESSING) }
        runCatching { processor.process(s.noteId, defaultTitle(), s.createdAt) }
            .onSuccess {
                session = null
                _uiState.update { it.copy(phase = RecordPhase.SAVED, savedNoteId = s.noteId) }
            }
            .onFailure { e ->
                Log.e(TAG, "processing failed", e)
                _uiState.update { it.copy(phase = RecordPhase.FAILED) }
            }
    }

    /** PROCESS_FLOW: Retry re-runs from step 5 (render) when signals and meta exist. */
    fun retryProcessing() {
        val s = session ?: return
        if (_uiState.value.phase != RecordPhase.FAILED) return
        viewModelScope.launch {
            _uiState.update { it.copy(phase = RecordPhase.PROCESSING) }
            val result = runCatching {
                if (repository.readMeta(s.noteId) == null) {
                    processor.process(s.noteId, defaultTitle(), s.createdAt)
                } else {
                    processor.render(s.noteId)
                }
            }
            if (result.isSuccess) {
                session = null
                _uiState.update { it.copy(phase = RecordPhase.SAVED, savedNoteId = s.noteId) }
            } else {
                Log.e(TAG, "retry failed", result.exceptionOrNull())
                _uiState.update { it.copy(phase = RecordPhase.FAILED) }
            }
        }
    }

    fun deleteFailedNote() {
        if (_uiState.value.phase != RecordPhase.FAILED) return
        val s = session
        session = null
        viewModelScope.launch {
            s?.let { withContext(Dispatchers.IO) { repository.delete(it.noteId) } }
            _uiState.update { it.copy(phase = RecordPhase.CONFIRM) }
            startPreview()
        }
    }

    /** After navigating to the saved note: back to Confirm (F2 before every recording). */
    fun onSavedNoteShown() {
        if (_uiState.value.phase != RecordPhase.SAVED) return
        resetSignalBuffers()
        _uiState.update { it.copy(phase = RecordPhase.CONFIRM, savedNoteId = null, recordedMs = 0, lockOnFraction = 0f) }
    }

    /** Back to Preview, deleting the note dir. The SDK keeps running. */
    private fun discardSession(reason: RecordInfo?) {
        val s = session
        session = null
        _uiState.update { it.copy(phase = RecordPhase.PREVIEW, recordedMs = 0, lockOnFraction = 0f, info = reason) }
        viewModelScope.launch { s?.discard() }
    }

    private fun startTicker(): Job = viewModelScope.launch {
        while (isActive) {
            delay(TICK_MS)
            val now = SystemClock.elapsedRealtime()
            val s = session
            _uiState.update {
                it.copy(
                    facePresent = now - lastFaceAtMs < FACE_FRESH_MS,
                    recordedMs = if (it.phase == RecordPhase.RECORDING) s?.audioDurationMs ?: 0L else it.recordedMs,
                    lockOnFraction = if (it.phase == RecordPhase.LOCK_ON) {
                        ((now - lockOnStartedAtMs).toFloat() / LOCK_ON_TIMEOUT_MS).coerceIn(0f, 1f)
                    } else {
                        it.lockOnFraction
                    },
                )
            }
            when (_uiState.value.phase) {
                RecordPhase.LOCK_ON -> if (now - lockOnStartedAtMs > LOCK_ON_TIMEOUT_MS) {
                    discardSession(RecordInfo.LOCK_ON_TIMEOUT)
                }
                RecordPhase.RECORDING -> when {
                    s == null -> Unit
                    s.audioDurationMs >= SILENT_MIC_CHECK_MS && s.audioPeakAbs == 0 -> discardSession(RecordInfo.MIC_SILENT)
                    now - lastFaceAtMs > FACE_LOST_DISCARD_MS -> discardSession(RecordInfo.FACE_LOST)
                    s.audioDurationMs >= MAX_NOTE_DURATION_MS -> finishRecording()
                }
                else -> Unit
            }
        }
    }

    private fun resetSignalBuffers() {
        pulseWindow.clear()
        waveBuffer.clear()
        breathNormalised = 0f
        _uiState.update { it.copy(pulseBpm = null, breathingWave = emptyList()) }
    }

    private fun defaultTitle() = getApplication<Application>().getString(R.string.default_note_title)

    /** ON_STOP: foreground-only capture (TRD § ANDROID 15 NOTES). */
    fun onScreenStop() {
        when (_uiState.value.phase) {
            RecordPhase.LOCK_ON, RecordPhase.RECORDING -> {
                discardSession(RecordInfo.BACKGROUNDED)
                viewModelScope.launch { runCatching { capture.stop() } }
            }
            RecordPhase.CONFIRM, RecordPhase.PREVIEW -> viewModelScope.launch { runCatching { capture.stop() } }
            // Flush and processing finish on their own; their result survives in the ViewModel.
            RecordPhase.FLUSHING, RecordPhase.PROCESSING, RecordPhase.FAILED, RecordPhase.SAVED -> Unit
        }
    }

    override fun onCleared() {
        capture.processingStatus.removeObserver(statusObserver)
        capture.validationStatus.removeObserver(validationObserver)
        capture.error.removeObserver(errorObserver)
        liveBeats.reset()
        // viewModelScope is already cancelled here; the SDK is a process-wide singleton.
        kotlinx.coroutines.MainScope().launch { runCatching { capture.stop() } }
    }

    companion object {
        private const val TAG = "Heartnote"
        private const val PULSE_MEDIAN_WINDOW = 5
        private const val WAVE_WINDOW = 100          // ~10 s of ~10 Hz brUpperTrace
        private const val TICK_MS = 250L
        private const val FACE_FRESH_MS = 1000L
        private const val FACE_LOST_DISCARD_MS = 3000L
        const val LOCK_ON_TIMEOUT_MS = 20_000L
        const val MAX_NOTE_DURATION_MS = 60_000L
        private const val SDK_TAIL_FLUSH_MS = 2500L
        private const val SILENT_MIC_CHECK_MS = 2000L
    }
}
