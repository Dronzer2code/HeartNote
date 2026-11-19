package com.heartnote.app.ui.record

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import com.heartnote.app.BuildConfig
import com.heartnote.app.R
import com.presagetech.smartspectra.proto.MetricsProto

/**
 * Why is the camera slow? Watches what the phone itself reports (Battery saver,
 * thermal status) and, in debug builds, logs how often the SDK emits face and
 * cardio metrics — the SDK's effective frame rate as Heartnote sees it.
 *
 * `adb logcat -s HeartnoteCapture` shows one line every 5 s plus every early stop.
 * Nothing leaves the phone.
 */
class CaptureHealth(context: Context) {
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var faceEmissions = 0
    private var cardioEmissions = 0
    private var traceSamples = 0
    private var windowStartMs = SystemClock.elapsedRealtime()
    private var lastPowerCheckMs = 0L

    @StringRes
    var slowCameraHint: Int? = null
        private set

    fun onMetrics(metrics: MetricsProto.Metrics) {
        if (metrics.hasFace()) faceEmissions++
        if (metrics.hasCardio()) {
            cardioEmissions++
            traceSamples += metrics.cardio.arterialPressureTraceCount
        }
    }

    /** Called from the 250 ms ticker. */
    fun tick(phase: RecordPhase) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPowerCheckMs >= POWER_CHECK_MS) {
            lastPowerCheckMs = now
            slowCameraHint = when {
                power.isPowerSaveMode -> R.string.record_hint_power_save
                thermalStatus() >= PowerManager.THERMAL_STATUS_MODERATE -> R.string.record_hint_warm
                else -> null
            }
        }
        val elapsed = now - windowStartMs
        if (elapsed >= LOG_WINDOW_MS) {
            if (BuildConfig.DEBUG) {
                val s = elapsed / 1000f
                Log.d(
                    TAG,
                    "phase=$phase face/s=%.1f cardio/s=%.1f trace/s=%.1f powerSave=%b thermal=%d".format(
                        faceEmissions / s, cardioEmissions / s, traceSamples / s, power.isPowerSaveMode, thermalStatus(),
                    ),
                )
            }
            faceEmissions = 0
            cardioEmissions = 0
            traceSamples = 0
            windowStartMs = now
        }
    }

    private fun thermalStatus(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power.currentThermalStatus else 0

    companion object {
        const val TAG = "HeartnoteCapture"
        private const val LOG_WINDOW_MS = 5_000L
        private const val POWER_CHECK_MS = 2_000L
    }
}
