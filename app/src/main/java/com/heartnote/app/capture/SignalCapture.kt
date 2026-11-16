package com.heartnote.app.capture

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.Preview
import androidx.lifecycle.LiveData
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraError
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.ValidationStatus
import com.presagetech.smartspectra.proto.MetricsProto
import kotlinx.coroutines.flow.SharedFlow

/**
 * Wraps SmartSpectraSdk.shared (TRD § SDK INTEGRATION).
 *
 * Also emits the Day 1 spike log (tag [SPIKE_TAG]) answering TRD § UNRESOLVED
 * S1–S4 from raw sdk.metrics emissions. Remove the logging after the spike.
 */
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
        resetSpikeState()
        Log.i(SPIKE_TAG, "session start: sdkVersion=${sdk.version}")
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

    // ---- Day 1 spike logging ----

    private var emissionCount = 0L
    private var loggedType = false
    private var lastPulseTs = Long.MIN_VALUE
    private var lastPressureTs = Long.MIN_VALUE
    private var lastBreathRateTs = Long.MIN_VALUE
    private var lastBreathTraceTs = Long.MIN_VALUE
    private var pressureEverSeen = false

    private fun resetSpikeState() {
        emissionCount = 0
        lastPulseTs = Long.MIN_VALUE
        lastPressureTs = Long.MIN_VALUE
        lastBreathRateTs = Long.MIN_VALUE
        lastBreathTraceTs = Long.MIN_VALUE
        pressureEverSeen = false
    }

    fun logEmission(metrics: MetricsProto.Metrics) {
        emissionCount++
        if (!loggedType) {
            loggedType = true
            // S1: concrete type of the emitted object; collected via sdk.metricsFlow
            // (SharedFlow) — sdk.metrics (LiveData) carries the same object.
            Log.i(SPIKE_TAG, "S1 emission type=${metrics.javaClass.name} collectedVia=sdk.metricsFlow(SharedFlow)")
        }

        // S3: compare sample timestamps against both clocks on every emission.
        val arrivalElapsedMs = SystemClock.elapsedRealtime()
        val arrivalEpochMs = System.currentTimeMillis()

        val pulse = summarize("pulseRate", metrics.cardio.pulseRateList.map { it.timestamp }, lastPulseTs)
        lastPulseTs = pulse.second
        val pressure = summarize("bpTrace", metrics.cardio.arterialPressureTraceList.map { it.timestamp }, lastPressureTs)
        lastPressureTs = pressure.second
        if (metrics.cardio.arterialPressureTraceCount > 0) pressureEverSeen = true
        val breathRate = summarize("brRate", metrics.breathing.rateList.map { it.timestamp }, lastBreathRateTs)
        lastBreathRateTs = breathRate.second
        val breathTrace = summarize("brUpperTrace", metrics.breathing.upperTraceList.map { it.timestamp }, lastBreathTraceTs)
        lastBreathTraceTs = breathTrace.second

        val lastPulseValue = metrics.cardio.pulseRateList.lastOrNull()?.value
        val lastBreathValue = metrics.breathing.rateList.lastOrNull()?.value

        Log.d(
            SPIKE_TAG,
            "emission#$emissionCount arrivalElapsedMs=$arrivalElapsedMs arrivalEpochMs=$arrivalEpochMs " +
                "face=${metrics.hasFace()} cardio=${metrics.hasCardio()} breathing=${metrics.hasBreathing()} | " +
                "${pulse.first} lastVal=$lastPulseValue | ${pressure.first} | " +
                "${breathRate.first} lastVal=$lastBreathValue | ${breathTrace.first} | " +
                "expr n=${if (metrics.hasFace()) metrics.face.expressionCount else 0} | " +
                "S2 bpTraceEverNonEmpty=$pressureEverSeen"
        )
    }

    /** S4: counts total vs. new-since-last-emission samples per list. */
    private fun summarize(name: String, timestamps: List<Long>, lastSeen: Long): Pair<String, Long> {
        if (timestamps.isEmpty()) return "$name n=0 new=0" to lastSeen
        val newCount = timestamps.count { it > lastSeen }
        val text = "$name n=${timestamps.size} new=$newCount firstTs=${timestamps.first()} lastTs=${timestamps.last()}"
        return text to maxOf(lastSeen, timestamps.last())
    }

    companion object {
        const val SPIKE_TAG = "HeartnoteSpike"
    }
}
