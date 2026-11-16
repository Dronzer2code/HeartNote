package com.heartnote.app.ui.record

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.heartnote.app.capture.RecordingSession
import com.heartnote.app.capture.SignalCapture
import com.heartnote.app.data.NoteRepository
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraError
import com.presagetech.smartspectra.ValidationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** PROCESS_FLOW § RECORD SCREEN STATE MACHINE (Confirm = F2 arrives day 5). */
enum class RecordPhase { PREVIEW, LOCK_ON, RECORDING, FLUSHING, SAVED }

data class RecordUiState(
    val phase: RecordPhase = RecordPhase.PREVIEW,
    val sdkStatus: ProcessingStatus = ProcessingStatus.IDLE,
    val facePresent: Boolean = false,
    val pulseBpm: Int? = null,                      // 5-sample median (S5)
    val breathingWave: List<Float> = emptyList(),   // recent brUpperTrace values
    val breathingRpm: Int? = null,                  // only when a sample arrived < 10 s ago
    val recordedSeconds: Int = 0,
    val validationHint: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,                // discard/timeout reasons
    val savedNoteId: String? = null,
    val savedDurationMs: Long? = null,
)

class RecordViewModel(application: Application) : AndroidViewModel(application) {
    private val capture = SignalCapture()
    private val repository = NoteRepository(application.filesDir)

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState

    private var session: RecordingSession? = null
    private var tickerJob: Job? = null

    // All times below are SystemClock.elapsedRealtime() ms.
    private var lastFaceAtMs = Long.MIN_VALUE
    private var lockOnStartedAtMs = 0L
    private var breathRateAtMs = Long.MIN_VALUE

    private val pulseWindow = ArrayDeque<Float>()
    private var lastPulseUs = Long.MIN_VALUE
    private val waveBuffer = ArrayDeque<Float>()
    private var lastWaveUs = Long.MIN_VALUE

    private val statusObserver = Observer<ProcessingStatus> { status ->
        _uiState.update { it.copy(sdkStatus = status) }
    }
    private val validationObserver = Observer<ValidationStatus?> { validation ->
        _uiState.update { it.copy(validationHint = validation?.hint?.takeIf { h -> h.isNotBlank() }) }
    }
    private val errorObserver = Observer<SmartSpectraError?> { error ->
        if (error != null) {
            _uiState.update { it.copy(errorMessage = error.message) }
            if (_uiState.value.phase == RecordPhase.LOCK_ON || _uiState.value.phase == RecordPhase.RECORDING) {
                discardSession(null)
            }
        }
    }

    init {
        capture.processingStatus.observeForever(statusObserver)
        capture.validationStatus.observeForever(validationObserver)
        capture.error.observeForever(errorObserver)
        viewModelScope.launch {
            capture.metricsFlow.collect { metrics -> onMetrics(metrics) }
        }
    }

    private fun onMetrics(metrics: com.presagetech.smartspectra.proto.MetricsProto.Metrics) {
        val now = SystemClock.elapsedRealtime()
        // Emissions are split by type: a face-bearing emission refreshes presence.
        if (metrics.hasFace()) {
            lastFaceAtMs = now
            _uiState.update { it.copy(facePresent = true) }
        }

        session?.let { s ->
            val phase = _uiState.value.phase
            if (phase == RecordPhase.LOCK_ON || phase == RecordPhase.RECORDING || phase == RecordPhase.FLUSHING) {
                s.onMetrics(metrics)
            }
        }

        if (metrics.hasCardio()) {
            metrics.cardio.pulseRateList.forEach { sample ->
                if (sample.timestamp > lastPulseUs) {
                    lastPulseUs = sample.timestamp
                    pulseWindow.addLast(sample.value)
                    while (pulseWindow.size > PULSE_MEDIAN_WINDOW) pulseWindow.removeFirst()
                }
            }
            if (pulseWindow.isNotEmpty()) {
                val median = pulseWindow.sorted()[pulseWindow.size / 2]
                _uiState.update { it.copy(pulseBpm = median.toInt()) }
            }
            // F3: lock-on completes on the first pulseRate sample after LockOn entry.
            if (_uiState.value.phase == RecordPhase.LOCK_ON && metrics.cardio.pulseRateCount > 0) {
                beginRecording()
            }
        }

        if (metrics.hasBreathing()) {
            var waveChanged = false
            metrics.breathing.upperTraceList.forEach { sample ->
                if (sample.timestamp > lastWaveUs) {
                    lastWaveUs = sample.timestamp
                    waveBuffer.addLast(sample.value)
                    while (waveBuffer.size > WAVE_WINDOW) waveBuffer.removeFirst()
                    waveChanged = true
                }
            }
            if (waveChanged) {
                _uiState.update { it.copy(breathingWave = waveBuffer.toList()) }
            }
            metrics.breathing.rateList.lastOrNull { it.timestamp > 0 }?.let { sample ->
                breathRateAtMs = now
                _uiState.update { it.copy(breathingRpm = sample.value.toInt()) }
            }
        }
    }

    fun attachPreview(surfaceProvider: Preview.SurfaceProvider) {
        capture.attachPreview(surfaceProvider)
    }

    /** Called when camera + mic permissions are granted; SDK runs from Preview on. */
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
            _uiState.update { it.copy(errorMessage = null) }
            runCatching { capture.start() }
                .onFailure { e ->
                    Log.e(TAG, "sdk.start() failed", e)
                    _uiState.update { it.copy(errorMessage = e.message ?: "Could not start the camera") }
                }
        }
    }

    /** Preview → LockOn. Requires a face emission within the last 1000 ms. */
    fun tapStart() {
        if (_uiState.value.phase != RecordPhase.PREVIEW || !_uiState.value.facePresent) return
        session = RecordingSession(repository)
        lockOnStartedAtMs = SystemClock.elapsedRealtime()
        _uiState.update { it.copy(phase = RecordPhase.LOCK_ON, infoMessage = null, errorMessage = null) }
        startTicker()
    }

    fun tapCancelLockOn() {
        if (_uiState.value.phase == RecordPhase.LOCK_ON) discardSession(null)
    }

    fun tapStop() {
        if (_uiState.value.phase == RecordPhase.RECORDING) finishRecording()
    }

    fun tapRecordAnother() {
        if (_uiState.value.phase != RecordPhase.SAVED) return
        resetSignalBuffers()
        _uiState.update {
            it.copy(
                phase = RecordPhase.PREVIEW, savedNoteId = null, savedDurationMs = null,
                recordedSeconds = 0, infoMessage = null,
            )
        }
        startPreview()
    }

    /** LockOn → Recording: audio starts here; lock-on silence is not in the note. */
    private fun beginRecording() {
        val s = session ?: return
        _uiState.update { it.copy(phase = RecordPhase.RECORDING, recordedSeconds = 0) }
        s.startAudio(viewModelScope) { e ->
            Log.e(TAG, "audio capture failed", e)
            viewModelScope.launch {
                _uiState.update { it.copy(errorMessage = "Microphone error — recording discarded") }
                discardSession(null)
            }
        }
    }

    /** Recording → Flushing (2.5 s SDK tail flush) → write files → Saved. */
    private fun finishRecording() {
        val s = session ?: return
        stopTicker()
        _uiState.update { it.copy(phase = RecordPhase.FLUSHING) }
        viewModelScope.launch {
            val durationMs = s.stopAudio()
            delay(SDK_TAIL_FLUSH_MS) // metrics keep flowing into the session meanwhile
            runCatching { capture.stop() }
            runCatching { withContext(Dispatchers.IO) { s.writeSignals(durationMs) } }
                .onSuccess {
                    session = null
                    _uiState.update {
                        it.copy(phase = RecordPhase.SAVED, savedNoteId = s.noteId, savedDurationMs = durationMs)
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "saving signals failed", e)
                    s.discard()
                    session = null
                    _uiState.update {
                        it.copy(phase = RecordPhase.PREVIEW, errorMessage = "Could not finish this note")
                    }
                    startPreview()
                }
        }
    }

    /** Back to Preview, deleting the note dir. The SDK keeps running. */
    private fun discardSession(reason: String?) {
        stopTicker()
        val s = session
        session = null
        _uiState.update { it.copy(phase = RecordPhase.PREVIEW, recordedSeconds = 0, infoMessage = reason) }
        viewModelScope.launch { s?.discard() }
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val facePresent = now - lastFaceAtMs < FACE_FRESH_MS
                val rpmFresh = now - breathRateAtMs < BREATH_RATE_FRESH_MS
                _uiState.update {
                    it.copy(
                        facePresent = facePresent,
                        breathingRpm = if (rpmFresh) it.breathingRpm else null,
                        recordedSeconds = (session?.audioDurationMs ?: 0L).toInt() / 1000,
                    )
                }
                when (_uiState.value.phase) {
                    RecordPhase.LOCK_ON -> if (now - lockOnStartedAtMs > LOCK_ON_TIMEOUT_MS) {
                        discardSession("Couldn't find your pulse. Move to better light and try again.")
                    }
                    RecordPhase.RECORDING -> {
                        if (now - lastFaceAtMs > FACE_LOST_DISCARD_MS) {
                            discardSession("Face lost — recording discarded. Try again.")
                        } else if ((session?.audioDurationMs ?: 0L) >= MAX_NOTE_DURATION_MS) {
                            finishRecording()
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun resetSignalBuffers() {
        pulseWindow.clear()
        waveBuffer.clear()
        lastPulseUs = Long.MIN_VALUE
        lastWaveUs = Long.MIN_VALUE
        _uiState.update { it.copy(pulseBpm = null, breathingWave = emptyList(), breathingRpm = null) }
    }

    /** ON_STOP: foreground-only capture (TRD § ANDROID 15 NOTES). */
    fun onScreenStop() {
        when (_uiState.value.phase) {
            RecordPhase.LOCK_ON, RecordPhase.RECORDING -> {
                discardSession("Recording stopped — Heartnote went to the background.")
                viewModelScope.launch { runCatching { capture.stop() } }
            }
            RecordPhase.PREVIEW -> viewModelScope.launch { runCatching { capture.stop() } }
            RecordPhase.FLUSHING, RecordPhase.SAVED -> Unit // flush finishes within 2.5 s
        }
    }

    override fun onCleared() {
        capture.processingStatus.removeObserver(statusObserver)
        capture.validationStatus.removeObserver(validationObserver)
        capture.error.removeObserver(errorObserver)
    }

    companion object {
        private const val TAG = "Heartnote"
        private const val PULSE_MEDIAN_WINDOW = 5
        private const val WAVE_WINDOW = 300          // ~10 s of ~30 Hz brUpperTrace
        private const val TICK_MS = 250L
        private const val FACE_FRESH_MS = 1000L
        private const val FACE_LOST_DISCARD_MS = 3000L
        private const val BREATH_RATE_FRESH_MS = 10_000L
        private const val LOCK_ON_TIMEOUT_MS = 20_000L
        private const val MAX_NOTE_DURATION_MS = 60_000L
        private const val SDK_TAIL_FLUSH_MS = 2500L
    }
}
