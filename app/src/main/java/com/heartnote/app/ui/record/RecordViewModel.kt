package com.heartnote.app.ui.record

import android.util.Log
import androidx.camera.core.Preview
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heartnote.app.capture.SignalCapture
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraError
import com.presagetech.smartspectra.ValidationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecordUiState(
    val status: ProcessingStatus = ProcessingStatus.IDLE,
    val pulseBpm: Int? = null,
    val breathingRpm: Int? = null,
    val hasFace: Boolean = false,
    val hasCardio: Boolean = false,
    val validationHint: String? = null,
    val errorMessage: String? = null,
)

class RecordViewModel : ViewModel() {
    private val capture = SignalCapture()

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState

    private val statusObserver = Observer<ProcessingStatus> { status ->
        _uiState.update { it.copy(status = status) }
    }
    private val validationObserver = Observer<ValidationStatus?> { validation ->
        _uiState.update { it.copy(validationHint = validation?.hint?.takeIf { h -> h.isNotBlank() }) }
    }
    private val errorObserver = Observer<SmartSpectraError?> { error ->
        _uiState.update { it.copy(errorMessage = error?.message) }
    }

    init {
        capture.processingStatus.observeForever(statusObserver)
        capture.validationStatus.observeForever(validationObserver)
        capture.error.observeForever(errorObserver)
        viewModelScope.launch {
            capture.metricsFlow.collect { metrics ->
                capture.logEmission(metrics)
                _uiState.update { state ->
                    state.copy(
                        hasFace = metrics.hasFace(),
                        hasCardio = metrics.hasCardio(),
                        pulseBpm = metrics.cardio.pulseRateList
                            .lastOrNull { it.timestamp > 0 }?.value?.toInt()
                            ?: state.pulseBpm,
                        breathingRpm = metrics.breathing.rateList
                            .lastOrNull { it.timestamp > 0 }?.value?.toInt()
                            ?: state.breathingRpm,
                    )
                }
            }
        }
    }

    fun attachPreview(surfaceProvider: Preview.SurfaceProvider) {
        capture.attachPreview(surfaceProvider)
    }

    fun toggleCapture() {
        when (_uiState.value.status) {
            ProcessingStatus.RUNNING -> stopCapture()
            ProcessingStatus.STARTING, ProcessingStatus.STOPPING -> Unit
            else -> startCapture()
        }
    }

    private fun startCapture() {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null, pulseBpm = null, breathingRpm = null) }
            runCatching { capture.start() }
                .onFailure { e ->
                    Log.e(SignalCapture.SPIKE_TAG, "sdk.start() failed", e)
                    _uiState.update { it.copy(errorMessage = e.message ?: "Could not start") }
                }
        }
    }

    fun stopCapture() {
        viewModelScope.launch {
            runCatching { capture.stop() }
                .onFailure { e -> Log.e(SignalCapture.SPIKE_TAG, "sdk.stop() failed", e) }
        }
    }

    override fun onCleared() {
        capture.processingStatus.removeObserver(statusObserver)
        capture.validationStatus.removeObserver(validationObserver)
        capture.error.removeObserver(errorObserver)
    }
}
