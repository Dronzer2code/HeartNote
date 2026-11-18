package com.heartnote.app.ui.playback

import com.heartnote.app.audio.HeartbeatSynth
import com.heartnote.app.ui.beat.BeatEvent
import com.heartnote.app.ui.beat.BeatKind

/**
 * Which LUB/DUB events a playback position has crossed (TRD § HAPTICS sync).
 * Pure Kotlin so the beat → event path is unit-tested; NotePlayer feeds it
 * ExoPlayer positions every 10 ms. Events always carry the beat's note time,
 * which is what makes HeartbeatHaptics vibrate.
 */
class BeatCursor(beatsMs: List<Long>) {
    private class Tick(val timeMs: Long, val kind: BeatKind, val beatMs: Long, val dubDelayMs: Long)

    private val ticks: List<Tick> = beatsMs.sorted().let { beats ->
        beats.flatMapIndexed { i, beat ->
            val dub = HeartbeatSynth.dubDelayMs(HeartbeatSynth.rrAt(beats, i))
            listOf(Tick(beat, BeatKind.LUB, beat, dub), Tick(beat + dub, BeatKind.DUB, beat, dub))
        }.sortedBy { it.timeMs }
    }
    private var cursor = 0
    private var lastPosition = 0L

    val beatCount: Int get() = ticks.size / 2

    /** Seek or restart: the next event is the first one after [positionMs]. */
    fun reset(positionMs: Long) {
        cursor = ticks.indexOfFirst { it.timeMs >= positionMs }.let { if (it < 0) ticks.size else it }
        lastPosition = positionMs
    }

    /** Emits every event with time in (last position, [positionMs]]; a backwards jump resets. */
    fun advance(positionMs: Long, emit: (BeatEvent) -> Unit) {
        if (positionMs < lastPosition) reset(positionMs)
        while (cursor < ticks.size && ticks[cursor].timeMs <= positionMs) {
            val t = ticks[cursor++]
            emit(BeatEvent(t.kind, if (t.kind == BeatKind.LUB) 1f else 0.5f, t.beatMs, t.dubDelayMs))
        }
        lastPosition = positionMs
    }
}
