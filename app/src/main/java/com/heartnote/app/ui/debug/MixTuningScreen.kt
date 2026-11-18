package com.heartnote.app.ui.debug

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.heartnote.app.audio.HeartbeatSynth
import com.heartnote.app.audio.MixSettings
import com.heartnote.app.audio.Mixer
import com.heartnote.app.data.NoteMeta
import com.heartnote.app.data.NoteProcessor
import com.heartnote.app.data.NoteRepository
import com.heartnote.app.haptics.HapticUsage
import com.heartnote.app.haptics.HapticsSettings
import com.heartnote.app.haptics.HeartbeatHaptics
import com.heartnote.app.ui.beat.BeatKind
import com.heartnote.app.ui.components.CircleIconButton
import com.heartnote.app.ui.components.HeartnoteIcons
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnDims
import com.heartnote.app.ui.theme.HnType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Debug builds only: persisted override of [MixSettings.current]. */
object DebugMixPrefs {
    private const val PREFS = "heartnote_debug"

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("voice_db")) return
        MixSettings.current = MixSettings(p.getFloat("voice_db", MixSettings.DEFAULT_VOICE_DB), p.getFloat("hb_db", MixSettings.DEFAULT_HEARTBEAT_DB))
    }

    fun save(context: Context, settings: MixSettings) {
        MixSettings.current = settings
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("voice_db", settings.voiceGainDb)
            .putFloat("hb_db", settings.heartbeatGainDb)
            .apply()
    }
}

/**
 * Debug-only "Mix tuning" (Fix 5): re-mix the latest own note's voice.wav live
 * with two gains, then use / apply the values. Also the API 33+ vibration
 * usage switch (media vs touch) for the strength test.
 */
@Composable
fun MixTuningScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { NoteRepository(context.filesDir) }
    var note by remember { mutableStateOf<NoteMeta?>(null) }
    var voiceDb by remember { mutableFloatStateOf(MixSettings.current.voiceGainDb) }
    var heartDb by remember { mutableFloatStateOf(MixSettings.current.heartbeatGainDb) }
    var status by remember { mutableStateOf("") }
    val player = remember { LivePlayer() }
    val usage by HapticsSettings.usage.collectAsState()
    val haptics = remember { HeartbeatHaptics(context) }

    LaunchedEffect(Unit) {
        note = withContext(Dispatchers.IO) {
            repository.list().firstOrNull { !it.isReceived && repository.voiceFile(it.id).exists() }
        }
        status = note?.let { "Latest note: ${it.title} (${it.durationMs / 1000} s)" } ?: "No note with voice.wav yet — record one first."
    }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    fun render(n: NoteMeta, settings: MixSettings): ShortArray {
        val voice = Mixer.readWavPcm16(repository.voiceFile(n.id))
        val beats = repository.readSignals(n.id).beatsMs
        return Mixer.mix(voice, HeartbeatSynth.render(beats, n.durationMs), settings)
    }

    fun play(fromMs: Long = 0) {
        val n = note ?: return
        val settings = MixSettings(voiceDb, heartDb)
        scope.launch {
            val pcm = withContext(Dispatchers.Default) { render(n, settings) }
            player.play(pcm, fromMs)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(HnColors.LightBg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(HnDims.screenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircleIconButton(HeartnoteIcons.Back, "Back", onBack)
        Text("Mix tuning (debug)", style = HnType.cardTitle, color = HnColors.InkBlack)
        Text(status, style = HnType.bodySecondary, color = HnColors.InkGrey)

        Text("Voice gain: ${"%.0f".format(voiceDb)} dB (after −24 dBFS RMS normalisation)", style = HnType.bodyPrimary, color = HnColors.InkBlack)
        Slider(
            value = voiceDb,
            onValueChange = { voiceDb = it },
            onValueChangeFinished = { if (player.isPlaying) play(player.positionMs()) },
            valueRange = MixSettings.VOICE_RANGE,
            steps = 17,
            colors = SliderDefaults.colors(thumbColor = HnColors.InkBlack, activeTrackColor = HnColors.InkBlack),
        )
        Text("Heartbeat gain: ${"%+.0f".format(heartDb)} dB (relative to −3 dBFS peak)", style = HnType.bodyPrimary, color = HnColors.InkBlack)
        Slider(
            value = heartDb,
            onValueChange = { heartDb = it },
            onValueChangeFinished = { if (player.isPlaying) play(player.positionMs()) },
            valueRange = MixSettings.HEARTBEAT_RANGE,
            steps = 17,
            colors = SliderDefaults.colors(thumbColor = HnColors.DotRed, activeTrackColor = HnColors.DotRed),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { if (player.isPlaying) player.stop() else play() },
                enabled = note != null,
                colors = ButtonDefaults.buttonColors(containerColor = HnColors.InkBlack),
            ) { Text(if (player.isPlaying) "Stop" else "Play") }
            OutlinedButton(onClick = {
                DebugMixPrefs.save(context, MixSettings(voiceDb, heartDb))
                status = "Saved as default: voice ${"%.0f".format(voiceDb)} dB, heartbeat ${"%+.0f".format(heartDb)} dB"
            }) { Text("Use these values") }
        }
        OutlinedButton(
            enabled = note != null,
            onClick = {
                val n = note ?: return@OutlinedButton
                DebugMixPrefs.save(context, MixSettings(voiceDb, heartDb))
                status = "Re-rendering ${n.title}…"
                scope.launch {
                    val ok = runCatching {
                        NoteProcessor(repository).render(n.id)
                        withContext(Dispatchers.IO) { repository.shareDir(n.id).listFiles()?.forEach { it.delete() } }
                    }.isSuccess
                    status = if (ok) "Applied to ${n.title}: heartnote.m4a re-mixed, share files cleared" else "Re-render failed"
                }
            },
        ) { Text("Apply to latest note (re-render + clear share cache)") }

        Text("Vibration usage (API 33+): ${usage.name}", style = HnType.bodyPrimary, color = HnColors.InkBlack, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HapticUsage.entries.forEach { u ->
                OutlinedButton(onClick = { HapticsSettings.setUsage(context, u); haptics.beat(BeatKind.LUB) }, modifier = Modifier.heightIn(min = HnDims.minTouch)) {
                    Text(u.name + if (u == usage) " ✓" else "")
                }
            }
            OutlinedButton(onClick = { haptics.beat(BeatKind.LUB) }) { Text("Feel it") }
        }
        Text(
            "Test with the phone on Silent too; record which usage feels stronger and isn't muted in TRD.md § HAPTICS.",
            style = HnType.caption,
            color = HnColors.InkGrey,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** In-memory AudioTrack player for the live re-mix. */
private class LivePlayer {
    private var track: AudioTrack? = null
    var isPlaying by mutableStateOf(false)
        private set

    fun play(pcm: ShortArray, fromMs: Long) {
        stop()
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(HeartbeatSynth.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        t.write(pcm, 0, pcm.size)
        t.playbackHeadPosition = (fromMs * HeartbeatSynth.SAMPLE_RATE / 1000).toInt().coerceIn(0, pcm.size - 1)
        t.play()
        track = t
        isPlaying = true
    }

    fun positionMs(): Long = track?.let { it.playbackHeadPosition * 1000L / HeartbeatSynth.SAMPLE_RATE } ?: 0L

    fun stop() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
        isPlaying = false
    }
}
