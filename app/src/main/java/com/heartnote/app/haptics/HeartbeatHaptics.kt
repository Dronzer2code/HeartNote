package com.heartnote.app.haptics

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.heartnote.app.audio.HeartbeatSynth
import com.heartnote.app.ui.beat.BeatEvent
import com.heartnote.app.ui.beat.BeatKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class HapticStrength { SOFT, MEDIUM, STRONG }

/** Which vibration usage to play with (API 33+ test: media vs touch). */
enum class HapticUsage { MEDIA, TOUCH }

/** Persisted haptics preferences (strength shown in Privacy & settings; usage is a debug choice). */
object HapticsSettings {
    private const val PREFS = "heartnote"
    private const val KEY_STRENGTH = "haptic_strength"
    private const val KEY_USAGE = "haptic_usage"

    private val _strength = MutableStateFlow(HapticStrength.STRONG)
    val strength: StateFlow<HapticStrength> = _strength
    private val _usage = MutableStateFlow(HapticUsage.MEDIA)
    val usage: StateFlow<HapticUsage> = _usage

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _strength.value = runCatching { HapticStrength.valueOf(p.getString(KEY_STRENGTH, null)!!) }.getOrDefault(HapticStrength.STRONG)
        _usage.value = runCatching { HapticUsage.valueOf(p.getString(KEY_USAGE, null)!!) }.getOrDefault(HapticUsage.MEDIA)
    }

    fun setStrength(context: Context, value: HapticStrength) {
        _strength.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STRENGTH, value.name).apply()
    }

    fun setUsage(context: Context, value: HapticUsage) {
        _usage.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_USAGE, value.name).apply()
    }
}

/**
 * Per-beat vibration (TRD § HAPTICS, revised 2026-11-18).
 * STRONG / MEDIUM: with amplitude control, ONE waveform per beat on the LUB
 * event — [0, 70, dubGap − 70, 45] ms at [0, 255, 0, 200] (medium 180/140);
 * the DUB event is then skipped. Without amplitude control: a 70 ms one-shot
 * on LUB and a 45 ms one-shot on DUB. SOFT: the THUD/CLICK composition.
 */
class HeartbeatHaptics(private val context: Context) {
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

    fun isAvailable(): Boolean = vibrator.hasVibrator()

    /**
     * Best-effort "vibration may be off" check for the hint. The user's
     * media-vibration intensity is a hidden setting (reading it throws on
     * Android 12+), so: no vibrator, ringer on Silent, or — for TOUCH usage —
     * touch feedback switched off.
     */
    fun mayBeOff(): Boolean {
        if (!vibrator.hasVibrator()) return true
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio?.ringerMode == AudioManager.RINGER_MODE_SILENT) return true
        if (HapticsSettings.usage.value == HapticUsage.TOUCH) {
            val touch = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) }.getOrDefault(1)
            if (touch == 0) return true
        }
        return false
    }

    /** BeatBus events: only playback beats (with a note time) vibrate. */
    fun play(event: BeatEvent) {
        if (event.noteTimeMs == null) return
        beat(event.kind, event.dubDelayMs)
    }

    /** One beat sound's vibration (playback, tour page 2, sample beat, settings test). */
    fun beat(kind: BeatKind, dubDelayMs: Long? = null) {
        val strength = HapticsSettings.strength.value
        val effect: VibrationEffect = when {
            strength == HapticStrength.SOFT -> soft(kind)
            vibrator.hasAmplitudeControl() -> {
                if (kind == BeatKind.DUB) return // already inside the LUB waveform
                val gap = (dubDelayMs ?: HeartbeatSynth.dubDelayMs(DEFAULT_RR_MS)).coerceAtLeast(LUB_MS + 10)
                val (lubAmp, dubAmp) = if (strength == HapticStrength.STRONG) 255 to 200 else 180 to 140
                VibrationEffect.createWaveform(
                    longArrayOf(0, LUB_MS, gap - LUB_MS, DUB_MS),
                    intArrayOf(0, lubAmp, 0, dubAmp),
                    -1,
                )
            }
            else -> VibrationEffect.createOneShot(if (kind == BeatKind.LUB) LUB_MS else DUB_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrate(effect, kind, strength)
    }

    private fun soft(kind: BeatKind): VibrationEffect {
        val lub = kind == BeatKind.LUB
        return when {
            primitive != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                VibrationEffect.startComposition().addPrimitive(primitive, if (lub) 1.0f else 0.5f).compose()
            vibrator.hasAmplitudeControl() -> VibrationEffect.createOneShot(if (lub) 40L else 25L, if (lub) 255 else 140)
            else -> VibrationEffect.createOneShot(if (lub) 40L else 25L, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    private fun vibrate(effect: VibrationEffect, kind: BeatKind, strength: HapticStrength) {
        val usage = HapticsSettings.usage.value
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val u = if (usage == HapticUsage.TOUCH) VibrationAttributes.USAGE_TOUCH else VibrationAttributes.USAGE_MEDIA
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(u))
            } else {
                val u = if (usage == HapticUsage.TOUCH) AudioAttributes.USAGE_ASSISTANCE_SONIFICATION else AudioAttributes.USAGE_MEDIA
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(u).build())
            }
            HapticsLog.d { "vibrate $kind ok strength=$strength usage=$usage amplitude=${vibrator.hasAmplitudeControl()} primitive=$primitive" }
        } catch (e: Exception) {
            HapticsLog.e("vibrate $kind failed", e)
        }
    }

    companion object {
        private const val LUB_MS = 70L
        private const val DUB_MS = 45L
        private const val DEFAULT_RR_MS = 800L

        /** "Open vibration settings" from the hint and the settings sheet. */
        fun openSoundSettings(context: Context) {
            context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
