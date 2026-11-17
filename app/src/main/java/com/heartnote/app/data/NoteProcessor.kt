package com.heartnote.app.data

import com.heartnote.app.audio.AacExporter
import com.heartnote.app.audio.HeartbeatSynth
import com.heartnote.app.audio.Mixer
import com.heartnote.app.signal.BeatExtractor
import com.heartnote.app.signal.SmileDetector
import com.heartnote.app.signal.TimedValue
import com.heartnote.app.signal.medianSmooth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PROCESS_FLOW § PROCESSING PIPELINE (F6). Steps 1–4 analyse and write the
 * signal track + meta; steps 5–8 render, export and drop voice.wav.
 * On failure voice.wav and signals.json are kept so [render] can be retried.
 */
class NoteProcessor(private val repository: NoteRepository) {

    suspend fun process(noteId: String, defaultTitle: String, createdAt: Long): NoteMeta =
        withContext(Dispatchers.Default) {
            val signals = repository.readSignals(noteId)
            val duration = signals.durationMs
            val raw = signals.raw

            val beats = BeatExtractor.extract(
                trace = raw.bpTrace.map { TimedValue(it.tMs, it.v) },
                pulseRate = raw.pulseRate.map { TimedValue(it.tMs, it.v) },
                durationMs = duration,
            )
            val smiles = SmileDetector.detect(
                raw.expressions.filter { it.type == SmileDetector.HAPPY }.map { TimedValue(it.tMs, it.c) },
                duration,
            )
            val pulseValues = medianSmooth(raw.pulseRate.map { it.v }, 5)

            val complete = signals.copy(
                beatSource = beats.source.key,
                beatsMs = beats.beatsMs,
                breathing = raw.brUpperTrace.filter { it.tMs in 0..duration }.map { CurvePoint(it.tMs, it.v) },
                pulseRate = raw.pulseRate.mapIndexed { i, s -> PulsePoint(s.tMs, pulseValues[i]) },
                smiles = smiles.map { SmileSegment(it.startMs, it.endMs) },
            )
            repository.writeSignals(noteId, complete)

            val meta = NoteMeta(
                id = noteId,
                title = repository.readMeta(noteId)?.title ?: defaultTitle,
                createdAt = createdAt,
                durationMs = duration,
                beatCount = beats.beatsMs.size,
                beatSource = beats.source.key,
                smileCount = smiles.size,
            )
            repository.writeMeta(meta)
            render(noteId, complete)
            meta
        }

    /** Steps 5–8: synth, mix, export heartnote.m4a, then delete voice.wav. */
    suspend fun render(noteId: String, signals: SignalsFile = repository.readSignals(noteId)) =
        withContext(Dispatchers.Default) {
            val voice = Mixer.readWavPcm16(repository.voiceFile(noteId))
            val heartbeat = HeartbeatSynth.render(signals.beatsMs, signals.durationMs)
            val mixed = Mixer.mix(voice, heartbeat)
            AacExporter.export(mixed, HeartbeatSynth.SAMPLE_RATE, repository.audioFile(noteId))
            repository.voiceFile(noteId).delete()
        }
}
