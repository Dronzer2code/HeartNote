package com.heartnote.app.capture

import com.heartnote.app.data.NoteRepository
import com.heartnote.app.data.RawExpression
import com.heartnote.app.data.RawSample
import com.heartnote.app.data.RawSignals
import com.heartnote.app.data.RawTalking
import com.heartnote.app.data.SignalsFile
import com.heartnote.app.signal.SignalAligner
import com.presagetech.smartspectra.proto.MetricsProto
import kotlinx.coroutines.CoroutineScope

/**
 * Owns one note's shared timeline and raw-sample accumulation, from LockOn
 * entry through the SDK tail flush (TRD § TIMELINE ALIGNMENT, PROCESS_FLOW
 * § RECORDING PIPELINE). All methods are called from the ViewModel's
 * main-dispatcher scope; no internal locking needed.
 */
class RecordingSession(private val repository: NoteRepository) {
    val noteId: String = repository.createNote()

    private val audio = AudioCapture()

    private class RawPoint(val timestampUs: Long, val v: Float, val c: Float?, val stable: Boolean?)

    private val bpTrace = ArrayList<RawPoint>()
    private val pulseRate = ArrayList<RawPoint>()
    private val brUpperTrace = ArrayList<RawPoint>()
    private val brRate = ArrayList<RawPoint>()
    private val expressionEvents = ArrayList<MetricsProto.Expression>()
    private val talkingEvents = ArrayList<Pair<Long, Boolean>>() // (timestampUs, detected)

    // S4: lists are incremental; timestamp guards are a safety net only.
    private var lastBpUs = Long.MIN_VALUE
    private var lastPulseUs = Long.MIN_VALUE
    private var lastBrTraceUs = Long.MIN_VALUE
    private var lastBrRateUs = Long.MIN_VALUE
    private var lastExpressionUs = Long.MIN_VALUE
    private var lastTalkingUs = Long.MIN_VALUE

    val audioStartEpochMs: Long get() = audio.audioStartEpochMs
    val audioDurationMs: Long get() = audio.durationMs

    fun startAudio(scope: CoroutineScope, onError: (Throwable) -> Unit) {
        audio.start(scope, repository.voiceFile(noteId), onError)
    }

    suspend fun stopAudio(): Long = audio.stop()

    fun onMetrics(metrics: MetricsProto.Metrics) {
        if (metrics.hasCardio()) {
            metrics.cardio.arterialPressureTraceList.forEach { s ->
                if (s.timestamp > lastBpUs) {
                    lastBpUs = s.timestamp
                    bpTrace += RawPoint(s.timestamp, s.value, s.confidence, s.stable)
                }
            }
            metrics.cardio.pulseRateList.forEach { s ->
                if (s.timestamp > lastPulseUs) {
                    lastPulseUs = s.timestamp
                    pulseRate += RawPoint(s.timestamp, s.value, s.confidence, s.stable)
                }
            }
        }
        if (metrics.hasBreathing()) {
            metrics.breathing.upperTraceList.forEach { s ->
                if (s.timestamp > lastBrTraceUs) {
                    lastBrTraceUs = s.timestamp
                    brUpperTrace += RawPoint(s.timestamp, s.value, null, s.stable)
                }
            }
            metrics.breathing.rateList.forEach { s ->
                if (s.timestamp > lastBrRateUs) {
                    lastBrRateUs = s.timestamp
                    brRate += RawPoint(s.timestamp, s.value, s.confidence, s.stable)
                }
            }
        }
        if (metrics.hasFace()) {
            metrics.face.expressionList.forEach { expr ->
                if (expr.timestamp > lastExpressionUs) {
                    lastExpressionUs = expr.timestamp
                    expressionEvents += expr
                }
            }
            metrics.face.talkingList.forEach { s ->
                if (s.timestamp > lastTalkingUs) {
                    lastTalkingUs = s.timestamp
                    talkingEvents += s.timestamp to s.detected
                }
            }
        }
    }

    /**
     * Aligns everything to tMs and writes signals.json. Call after stopAudio()
     * and the 2.5 s SDK tail flush. Raw lists keep tMs < 0 samples on purpose.
     */
    fun writeSignals(durationMs: Long): SignalsFile {
        val start = audioStartEpochMs
        fun align(us: Long) = SignalAligner.toTimelineMs(us, start)
        fun List<RawPoint>.toRaw() = map { RawSample(align(it.timestampUs), it.v, it.c, it.stable) }

        val signals = SignalsFile(
            durationMs = durationMs,
            audioStartEpochMs = start,
            raw = RawSignals(
                bpTrace = bpTrace.toRaw(),
                pulseRate = pulseRate.toRaw(),
                brUpperTrace = brUpperTrace.toRaw(),
                brRate = brRate.toRaw(),
                expressions = expressionEvents.flatMap { expr ->
                    expr.scoresList.map { score ->
                        RawExpression(align(expr.timestamp), score.type.name, score.confidence)
                    }
                },
                talking = talkingEvents.map { (us, detected) -> RawTalking(align(us), detected) },
            ),
        )
        repository.writeSignals(noteId, signals)
        return signals
    }

    suspend fun discard() {
        runCatching { audio.stop() }
        repository.delete(noteId)
    }
}
