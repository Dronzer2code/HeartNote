package com.heartnote.app.capture

import androidx.camera.core.Preview
import androidx.lifecycle.LiveData
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraError
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.ValidationStatus
import com.presagetech.smartspectra.proto.MetricsProto
import kotlinx.coroutines.flow.SharedFlow

/** Wraps SmartSpectraSdk.shared (TRD § SDK INTEGRATION). */
class SignalCapture {
    private val sdk = SmartSpectraSdk.shared

    val metricsFlow: SharedFlow<MetricsProto.Metrics> get() = sdk.metricsFlow
    val processingStatus: LiveData<ProcessingStatus> get() = sdk.processingStatus
    val validationStatus: LiveData<ValidationStatus?> get() = sdk.validationStatus
    val error: LiveData<SmartSpectraError?> get() = sdk.error

    fun attachPreview(surfaceProvider: Preview.SurfaceProvider) {
        sdk.config.previewSurfaceProvider = surfaceProvider
    }

    suspend fun start() {
        sdk.start()
    }

    suspend fun stop() {
        when (sdk.processingStatus.value) {
            ProcessingStatus.RUNNING,
            ProcessingStatus.STARTING,
            ProcessingStatus.STOPPING,
            -> sdk.stop()
            else -> Unit
        }
    }
}
