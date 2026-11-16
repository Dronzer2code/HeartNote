package com.heartnote.app

import android.app.Application
import com.presagetech.smartspectra.CameraSelection
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.proto.MetricTypesProto.MetricType

class HeartnoteApp : Application() {
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
    }
}
