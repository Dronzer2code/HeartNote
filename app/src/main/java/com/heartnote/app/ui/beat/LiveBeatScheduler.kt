package com.heartnote.app.ui.beat

import com.heartnote.app.audio.HeartbeatSynth
import com.heartnote.app.signal.BeatExtractor
import com.heartnote.app.signal.TimedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DESIGN.md § BEAT SYNC — Live. bpTrace arrives ~1.5 s after the beat in ~1 s
 * chunks, so the sphere shows the user's real rhythm on a fixed 1.8 s delay:
 * keep 6 s of trace (epoch ms), detect beats on every chunk, emit only beats
 * newer than the last one, and schedule LUB at beat + 1.8 s and DUB after the
 * clamped dub delay. Events carry no note time, so they never vibrate.
 */
class LiveBeatScheduler(
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val displayDelayMs: Long = DISPLAY_DELAY_MS,
    /** Test hook: receives (wall-clock ms, kind, strength) instead of a timed emit. */
    private val scheduleHook: ((Long, BeatKind, Float) -> Unit)? = null,
) {
    private val trace = ArrayDeque<TimedValue>()
    private val pulse = ArrayDeque<TimedValue>()
    private var lastBeatMs = Long.MIN_VALUE
    private val pending = ArrayList<Job>()

    /** SDK samples: timestamps in epoch MICROseconds (TRD S3). */
    fun onTrace(samples: List<Pair<Long, Float>>) {
        if (samples.isEmpty()) return
        samples.forEach { (us, v) -> trace.addLast(TimedValue(us / 1000, v)) }
        val newest = trace.last().tMs
        while (trace.isNotEmpty() && trace.first().tMs < newest - BUFFER_MS) trace.removeFirst()
        detect(newest)
    }

    fun onPulseRate(samples: List<Pair<Long, Float>>) {
        samples.forEach { (us, bpm) -> pulse.addLast(TimedValue(us / 1000, bpm)) }
        while (pulse.size > PULSE_KEEP) pulse.removeFirst()
    }

    fun reset() {
        pending.forEach { it.cancel() }
        pending.clear()
        trace.clear()
        pulse.clear()
        lastBeatMs = Long.MIN_VALUE
    }

    private fun detect(newestMs: Long) {
        val beats = BeatExtractor.detectBeats(trace.toList(), pulse.toList())
        val wallNow = now()
        var previous = lastBeatMs
        for (beat in beats) {
            // A peak at the very end of the buffer may still move; take it next chunk.
            if (beat > newestMs - EDGE_GUARD_MS) break
            // Peak times shift slightly between runs: anything this close is the same beat.
            if (beat <= lastBeatMs + SAME_BEAT_MS) continue
            val target = beat + displayDelayMs
            if (target < wallNow - LATE_TOLERANCE_MS) {
                lastBeatMs = beat // backlog from the first chunk: too old to show
                previous = beat
                continue
            }
            // Beats at the start of a chunk can arrive ~2 s after they happened,
            // i.e. slightly after their 1.8 s slot; show those immediately.
            val lubAt = maxOf(target, wallNow)
            val rr = if (previous != Long.MIN_VALUE) beat - previous else DEFAULT_RR_MS
            schedule(lubAt, BeatKind.LUB, 1f)
            schedule(lubAt + HeartbeatSynth.dubDelayMs(rr.coerceIn(BeatExtractor.MIN_RR_MS, BeatExtractor.MAX_RR_MS)), BeatKind.DUB, 0.5f)
            lastBeatMs = beat
            previous = beat
        }
        pending.removeAll { !it.isActive }
    }

    private fun schedule(atMs: Long, kind: BeatKind, strength: Float) {
        scheduleHook?.let {
            it(atMs, kind, strength)
            return
        }
        pending += scope.launch {
            delay((atMs - now()).coerceAtLeast(0))
            BeatBus.emit(BeatEvent(kind, strength, noteTimeMs = null))
        }
    }

    companion object {
        /**
         * DESIGN.md value. With measured delivery (1 s chunks, median sample
         * 1.5 s old) about a third of beats arrive after this slot and show
         * up to ~0.4 s late; 2,300 ms puts every beat on its slot
         * (LiveBeatSchedulerTest). Kept at the spec value pending device check.
         */
        const val DISPLAY_DELAY_MS = 1_800L
        private const val BUFFER_MS = 6_000L
        private const val PULSE_KEEP = 10
        private const val EDGE_GUARD_MS = 150L
        private const val SAME_BEAT_MS = 300L
        private const val LATE_TOLERANCE_MS = 600L
        private const val DEFAULT_RR_MS = 800L
    }
}
