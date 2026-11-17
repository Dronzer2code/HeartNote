package com.heartnote.app.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.heartnote.app.ui.beat.BeatEvent
import com.heartnote.app.ui.beat.BeatKind

/**
 * TRD § HAPTICS. One effect per BeatBus event: LUB = full-strength primitive,
 * DUB = half strength (the dub delay is already in the event timing).
 */
class HeartbeatHaptics(context: Context) {
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private val primitive: Int? = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> null
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD) ->
            VibrationEffect.Composition.PRIMITIVE_THUD
        vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK) ->
            VibrationEffect.Composition.PRIMITIVE_CLICK
        else -> null
    }

    /**
     * False when the device has no vibrator. Whether the user switched media
     * vibration off is not readable by apps (the setting is hidden and throws
     * SecurityException from Android 12), so that case cannot be detected.
     */
    fun isAvailable(): Boolean = vibrator.hasVibrator()

    fun play(event: BeatEvent) {
        if (event.noteTimeMs == null) return
        val lub = event.kind == BeatKind.LUB
        val effect = when {
            primitive != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                VibrationEffect.startComposition()
                    .addPrimitive(primitive, if (lub) 1.0f else 0.5f)
                    .compose()
            vibrator.hasAmplitudeControl() ->
                VibrationEffect.createOneShot(if (lub) 40L else 25L, if (lub) 255 else 140)
            else -> VibrationEffect.createOneShot(if (lub) 40L else 25L, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build(),
            )
        }
    }
}
