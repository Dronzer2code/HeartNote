package com.heartnote.app.ui.beat

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

enum class BeatKind { LUB, DUB }

/**
 * [noteTimeMs] is the beat's position in a playing note; null for live
 * (recording) beats. Haptics react only to events with a note time, so a live
 * beat can never vibrate (DESIGN.md § BEAT SYNC rule 6).
 */
data class BeatEvent(
    val kind: BeatKind,
    val strength: Float,
    val noteTimeMs: Long?,
    /** Playback LUBs: time to this beat's DUB, so haptics can play lub + dub as one waveform. */
    val dubDelayMs: Long? = null,
)

/** Single source of beat events for visuals and haptics (DESIGN.md § BEAT SYNC). */
object BeatBus {
    private val _events = MutableSharedFlow<BeatEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<BeatEvent> = _events

    fun emit(event: BeatEvent) {
        _events.tryEmit(event)
    }
}
