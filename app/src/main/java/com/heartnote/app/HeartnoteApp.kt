package com.heartnote.app

import android.app.Application
import com.heartnote.app.haptics.HapticsSettings
import com.heartnote.app.haptics.HeartbeatHaptics
import com.heartnote.app.ui.beat.BeatBus
import com.presagetech.smartspectra.CameraSelection
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.proto.MetricTypesProto.MetricType
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class HeartnoteApp : Application() {
    private val appScope = MainScope()

    override fun onCreate() {
        super.onCreate()
        val sdk = SmartSpectraSdk.shared
        sdk.config.apiKey = BuildConfig.PRESAGE_API_KEY
        // C3: camera frames must never be exposed or written to disk
        sdk.config.imageOutputEnabled = false
        sdk.config.requestedMetrics =
            SmartSpectraConfig.breathingMetrics +
                SmartSpectraConfig.cardioMetrics +
                listOf(MetricType.EXPRESSIONS, MetricType.TALKING)
        sdk.useCamera(CameraSelection.Front)

        // The only haptics subscriber: one vibration per playback beat event,
        // however many screens are observing the bus. Live beats never vibrate.
        HapticsSettings.load(this)
        val haptics = HeartbeatHaptics(this)
        appScope.launch { BeatBus.events.collect { haptics.play(it) } }
    }
}
