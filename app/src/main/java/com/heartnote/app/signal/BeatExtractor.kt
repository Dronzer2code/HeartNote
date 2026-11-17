package com.heartnote.app.signal

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** A sample on any millisecond timeline (note `tMs`, or epoch ms for live use). */
data class TimedValue(val tMs: Long, val v: Float)

enum class BeatSource(val key: String) {
    TRACE("trace"),
    RATE("rate"),
    MIXED("mixed"),
    ;

    companion object {
        fun fromKey(key: String?): BeatSource = entries.firstOrNull { it.key == key } ?: RATE
    }
}

data class BeatResult(val beatsMs: List<Long>, val source: BeatSource)

/** TRD § BEAT EXTRACTION. Pure Kotlin so it runs in JVM unit tests. */
object BeatExtractor {
    const val MIN_RR_MS = 333L   // 180 BPM
    const val MAX_RR_MS = 1500L  // 40 BPM
    const val PROMINENCE_FACTOR = 0.3

    private const val RESAMPLE_HZ = 30.0
    private const val DT_MS = 1000.0 / RESAMPLE_HZ
    private const val DETREND_WINDOW_S = 1.0
    private const val STD_WINDOW_S = 4.0
    private const val PROMINENCE_WINDOW_S = 0.5
    private const val SMALL_PEAK_FACTOR = 0.3
    /** Interval longer than this × expected RR = missed beat(s); shorter than the early factor = double detection. */
    private const val GAP_FACTOR = 1.6
    private const val EARLY_FACTOR = 0.55
    private const val SMALL_PEAK_WINDOW_MS = 3000L
    private const val REFERENCE_PERCENTILE = 0.8
    private const val MIN_CONSISTENT_FRACTION = 0.6
    private const val RR_TOLERANCE = 0.35
    private const val PULSE_MEDIAN_WINDOW = 5

    /**
     * Beats in [0, durationMs]. Trace samples before 0 are used as peak-detection
     * context; beats outside the note are dropped.
     */
    fun extract(trace: List<TimedValue>, pulseRate: List<TimedValue>, durationMs: Long): BeatResult {
        val peaks = detectPeaks(trace)
        val rate = RateCurve.from(pulseRate, peaks)

        if (rate == null) {
            val inNote = peaks.filter { it in 0..durationMs }
            return BeatResult(inNote, BeatSource.TRACE)
        }

        if (!traceIsUsable(peaks, rate)) {
            return BeatResult(rateBeats(rate, durationMs), BeatSource.RATE)
        }

        val (beats, filled) = fillGaps(dropEarlyPeaks(peaks, rate), rate, durationMs)
        val inNote = beats.filter { it in 0..durationMs }
        val anyFilledInNote = filled.any { it in 0..durationMs }
        return BeatResult(inNote, if (anyFilledInNote) BeatSource.MIXED else BeatSource.TRACE)
    }

    /**
     * Peaks with secondary waves and double detections removed, without gap
     * filling or clipping to a note — for LiveBeatScheduler (epoch-ms timeline).
     */
    fun detectBeats(trace: List<TimedValue>, pulseRate: List<TimedValue>): List<Long> {
        val peaks = detectPeaks(trace)
        val rate = RateCurve.from(pulseRate, peaks) ?: return peaks
        return dropEarlyPeaks(peaks, rate)
    }

    /**
     * Steps 1–3 of TRD § BEAT EXTRACTION: resample to 30 Hz, subtract a 1 s
     * centred moving average, keep local maxima ≥ 333 ms apart whose prominence
     * is ≥ 0.3 × the rolling 4 s standard deviation. Also used live by
     * LiveBeatScheduler on epoch-ms timestamps.
     */
    fun detectPeaks(trace: List<TimedValue>): List<Long> {
        val sorted = trace.sortedBy { it.tMs }.distinctBy { it.tMs }
        if (sorted.size < 10) return emptyList()
        val t0 = sorted.first().tMs
        val span = sorted.last().tMs - t0
        val n = floor(span / DT_MS).toInt() + 1
        if (n < 10) return emptyList()

        val x = resample(sorted, t0, n)
        val d = detrend(x, (DETREND_WINDOW_S * RESAMPLE_HZ).toInt() / 2)
        val std = rollingStd(d, (STD_WINDOW_S * RESAMPLE_HZ).toInt() / 2)
        val promHalf = (PROMINENCE_WINDOW_S * RESAMPLE_HZ).toInt()

        val candidates = ArrayList<Int>()
        for (i in 1 until n - 1) {
            if (d[i] > d[i - 1] && d[i] >= d[i + 1] && std[i] > 1e-9) {
                var leftMin = d[i]
                for (k in max(0, i - promHalf) until i) leftMin = min(leftMin, d[k])
                var rightMin = d[i]
                for (k in i + 1..min(n - 1, i + promHalf)) rightMin = min(rightMin, d[k])
                val prominence = d[i] - max(leftMin, rightMin)
                if (prominence >= PROMINENCE_FACTOR * std[i]) candidates += i
            }
        }

        // Minimum distance: keep the taller peak of any pair closer than MIN_RR.
        val minGap = (MIN_RR_MS / DT_MS).toInt()
        val accepted = ArrayList<Int>()
        for (i in candidates.sortedByDescending { d[it] }) {
            if (accepted.none { abs(it - i) < minGap }) accepted += i
        }
        accepted.sort()

        // Drop small secondary waves (e.g. dicrotic). The reference is a high
        // percentile, not the median: when secondary waves are about half of all
        // candidates, the median can itself be a secondary wave.
        val heights = accepted.map { d[it] }
        val times = accepted.map { refinedTimeMs(d, it, t0) }
        val kept = ArrayList<Long>()
        for (j in accepted.indices) {
            val neighbours = times.indices
                .filter { abs(times[it] - times[j]) <= SMALL_PEAK_WINDOW_MS }
                .map { heights[it] }
                .sorted()
            val reference = neighbours[((neighbours.size - 1) * REFERENCE_PERCENTILE).toInt()]
            if (heights[j] >= SMALL_PEAK_FACTOR * reference) kept += times[j]
        }
        return kept
    }

    private fun resample(s: List<TimedValue>, t0: Long, n: Int): DoubleArray {
        val out = DoubleArray(n)
        var j = 0
        for (i in 0 until n) {
            val t = t0 + i * DT_MS
            while (j < s.size - 2 && s[j + 1].tMs < t) j++
            val a = s[j]
            val b = s[min(j + 1, s.size - 1)]
            out[i] = if (b.tMs == a.tMs) {
                a.v.toDouble()
            } else {
                val f = ((t - a.tMs) / (b.tMs - a.tMs)).coerceIn(0.0, 1.0)
                a.v + (b.v - a.v) * f
            }
        }
        return out
    }

    private fun detrend(x: DoubleArray, half: Int): DoubleArray {
        val prefix = DoubleArray(x.size + 1)
        for (i in x.indices) prefix[i + 1] = prefix[i] + x[i]
        return DoubleArray(x.size) { i ->
            val lo = max(0, i - half)
            val hi = min(x.size - 1, i + half)
            x[i] - (prefix[hi + 1] - prefix[lo]) / (hi - lo + 1)
        }
    }

    private fun rollingStd(x: DoubleArray, half: Int): DoubleArray {
        val p1 = DoubleArray(x.size + 1)
        val p2 = DoubleArray(x.size + 1)
        for (i in x.indices) {
            p1[i + 1] = p1[i] + x[i]
            p2[i + 1] = p2[i] + x[i] * x[i]
        }
        return DoubleArray(x.size) { i ->
            val lo = max(0, i - half)
            val hi = min(x.size - 1, i + half)
            val count = hi - lo + 1
            val mean = (p1[hi + 1] - p1[lo]) / count
            sqrt(max(0.0, (p2[hi + 1] - p2[lo]) / count - mean * mean))
        }
    }

    /** Parabolic interpolation around sample i for sub-sample peak timing. */
    private fun refinedTimeMs(d: DoubleArray, i: Int, t0: Long): Long {
        val a = d[i - 1]
        val b = d[i]
        val c = d[i + 1]
        val denom = a - 2 * b + c
        val offset = if (abs(denom) < 1e-12) 0.0 else (0.5 * (a - c) / denom).coerceIn(-0.5, 0.5)
        return (t0 + (i + offset) * DT_MS).roundToLong()
    }

    /**
     * The trace is trusted if most beat-to-beat intervals agree (±35%) with the
     * measured pulse rate. Noise produces peaks at the minimum spacing and fails this.
     */
    private fun traceIsUsable(peaks: List<Long>, rate: RateCurve): Boolean {
        if (peaks.size < 3) return false
        val intervals = peaks.zipWithNext { a, b -> a to b - a }.filter { (_, rr) -> rr <= MAX_RR_MS }
        if (intervals.isEmpty()) return false
        val consistent = intervals.count { (t, rr) ->
            val expected = rate.rrMs(t)
            abs(rr - expected) <= expected * RR_TOLERANCE
        }
        return consistent >= intervals.size * MIN_CONSISTENT_FRACTION
    }

    private fun dropEarlyPeaks(peaks: List<Long>, rate: RateCurve): List<Long> {
        val out = ArrayList<Long>()
        for (p in peaks) {
            val prev = out.lastOrNull()
            if (prev == null || p - prev >= rate.rrMs(prev) * EARLY_FACTOR) out += p
        }
        return out
    }

    private fun isGap(a: Long, b: Long, rate: RateCurve): Boolean =
        b - a > min(MAX_RR_MS.toDouble(), rate.rrMs(a) * GAP_FACTOR)

    /**
     * Step 4: fill missed beats from the rate curve — intervals > 1.6 × the
     * expected interval (capped at MAX_RR), plus the note's start and end.
     */
    private fun fillGaps(peaks: List<Long>, rate: RateCurve, durationMs: Long): Pair<List<Long>, List<Long>> {
        val beats = ArrayList<Long>()
        val filled = ArrayList<Long>()

        // Lead-in: walk backwards from the first peak to 0.
        val first = peaks.first()
        if (isGap(0, first, rate)) {
            val lead = ArrayList<Long>()
            var t = first - rate.rrMs(first)
            while (t >= 0) {
                lead += t
                t -= rate.rrMs(t)
            }
            filled += lead
            beats += lead.reversed()
        }

        for (k in peaks.indices) {
            beats += peaks[k]
            if (k + 1 < peaks.size) {
                val a = peaks[k]
                val b = peaks[k + 1]
                if (isGap(a, b, rate)) {
                    // Spread the missing beats evenly so neither neighbour is crowded.
                    val missing = max(1L, Math.round((b - a).toDouble() / rate.rrMs(a)) - 1)
                    val step = (b - a) / (missing + 1)
                    var t = a + step
                    while (t < b - step / 2) {
                        beats += t
                        filled += t
                        t += step
                    }
                }
            }
        }

        val last = peaks.last()
        if (isGap(last, durationMs, rate)) {
            var t = last + rate.rrMs(last)
            while (t <= durationMs) {
                beats += t
                filled += t
                t += rate.rrMs(t)
            }
        }
        return beats to filled
    }

    /** Fallback: integrate phase from the pulse-rate curve, first beat half an interval in. */
    fun rateBeats(rate: RateCurve, durationMs: Long): List<Long> {
        val out = ArrayList<Long>()
        var t = rate.rrMs(0) / 2
        while (t <= durationMs) {
            out += t
            t += rate.rrMs(t)
        }
        return out
    }

    /** Linear interpolation of median-filtered pulse rate (S5), clamped to 40–180 BPM. */
    class RateCurve private constructor(private val points: List<TimedValue>) {
        fun bpmAt(tMs: Long): Double {
            if (points.size == 1 || tMs <= points.first().tMs) return points.first().v.toDouble()
            if (tMs >= points.last().tMs) return points.last().v.toDouble()
            val j = points.indexOfLast { it.tMs <= tMs }
            val a = points[j]
            val b = points[j + 1]
            val f = (tMs - a.tMs).toDouble() / (b.tMs - a.tMs)
            return a.v + (b.v - a.v) * f
        }

        fun rrMs(tMs: Long): Long =
            (60_000.0 / bpmAt(tMs).coerceIn(40.0, 180.0)).roundToLong().coerceIn(MIN_RR_MS, MAX_RR_MS)

        companion object {
            /** From pulse rate; falls back to the peaks' median interval; null if neither exists. */
            fun from(pulseRate: List<TimedValue>, peaks: List<Long>): RateCurve? {
                val sorted = pulseRate.sortedBy { it.tMs }.distinctBy { it.tMs }
                if (sorted.isNotEmpty()) {
                    val values = medianSmooth(sorted.map { it.v }, PULSE_MEDIAN_WINDOW)
                    return RateCurve(sorted.mapIndexed { i, s -> TimedValue(s.tMs, values[i]) })
                }
                if (peaks.size >= 3) {
                    val rr = peaks.zipWithNext { a, b -> b - a }
                        .filter { it in MIN_RR_MS..MAX_RR_MS }
                        .sorted()
                    if (rr.isNotEmpty()) {
                        return RateCurve(listOf(TimedValue(0, (60_000.0 / rr[rr.size / 2]).toFloat())))
                    }
                }
                return null
            }
        }
    }
}

/** Centred running median; window should be odd. */
fun medianSmooth(values: List<Float>, window: Int): List<Float> {
    val half = window / 2
    return values.indices.map { i ->
        val lo = max(0, i - half)
        val hi = min(values.size - 1, i + half)
        val slice = values.subList(lo, hi + 1).sorted()
        slice[slice.size / 2]
    }
}

/** Trailing median of the last [window] values (live display, S5). */
fun trailingMedian(values: Collection<Float>, window: Int): Float? {
    if (values.isEmpty()) return null
    val slice = values.toList().takeLast(window).sorted()
    return slice[slice.size / 2]
}
