package com.heartnote.app.signal

/** TRD § SMILE DETECTION (F8). */
object SmileDetector {
    const val HAPPY = "HAPPY"
    private const val MIN_CONFIDENCE = 0.6f
    private const val MIN_DURATION_MS = 500L
    private const val MERGE_GAP_MS = 1000L
    /** Two HAPPY samples further apart than this do not belong to one segment. */
    private const val MAX_SAMPLE_GAP_MS = 400L

    data class Segment(val startMs: Long, val endMs: Long)

    /** [happy] = HAPPY confidence samples on the note timeline. */
    fun detect(happy: List<TimedValue>, durationMs: Long): List<Segment> {
        val runs = ArrayList<Segment>()
        var runStart: Long? = null
        var runEnd = 0L
        for (s in happy.sortedBy { it.tMs }) {
            val high = s.v >= MIN_CONFIDENCE
            val start = runStart
            if (high && start != null && s.tMs - runEnd <= MAX_SAMPLE_GAP_MS) {
                runEnd = s.tMs
            } else {
                if (start != null) runs += Segment(start, runEnd)
                runStart = if (high) s.tMs else null
                runEnd = s.tMs
            }
        }
        runStart?.let { runs += Segment(it, runEnd) }

        val merged = ArrayList<Segment>()
        for (r in runs) {
            val prev = merged.lastOrNull()
            if (prev != null && r.startMs - prev.endMs < MERGE_GAP_MS) {
                merged[merged.size - 1] = Segment(prev.startMs, r.endMs)
            } else {
                merged += r
            }
        }
        return merged
            .filter { it.endMs - it.startMs >= MIN_DURATION_MS }
            .map { Segment(it.startMs.coerceAtLeast(0), it.endMs.coerceAtMost(durationMs)) }
            .filter { it.endMs > it.startMs }
    }
}
