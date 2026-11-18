package com.heartnote.app.haptics

import android.util.Log
import com.heartnote.app.BuildConfig

/**
 * Debug-only trace of the beat → vibration path (tag `HeartnoteHaptics`):
 * a) import result (payload or recovery), b) beats in the note's
 * signals.json, c) every fired beat, d) every vibrator call.
 * `adb logcat -s HeartnoteHaptics`
 */
object HapticsLog {
    const val TAG = "HeartnoteHaptics"

    fun d(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    fun e(message: String, error: Throwable) {
        Log.e(TAG, message, error)
    }
}
