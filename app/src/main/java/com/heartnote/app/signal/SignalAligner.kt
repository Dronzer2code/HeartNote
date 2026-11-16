package com.heartnote.app.signal

/**
 * TRD § TIMELINE ALIGNMENT: SDK sample timestamps are Unix epoch MICROSECONDS
 * on the same wall clock as System.currentTimeMillis(), so alignment is a
 * plain subtraction against the audio start anchor.
 */
object SignalAligner {
    fun toTimelineMs(timestampUs: Long, audioStartEpochMs: Long): Long =
        timestampUs / 1000 - audioStartEpochMs
}
