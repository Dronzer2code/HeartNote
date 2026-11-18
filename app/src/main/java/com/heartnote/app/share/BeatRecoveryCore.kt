package com.heartnote.app.share

import com.heartnote.app.audio.HeartbeatSynth
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * SHARE_AND_IMPORT.md § BEAT RECOVERY, steps 2–8, on 8 kHz mono PCM
 * (band 40–100 Hz instead of 40–220 Hz, see [BAND_HIGH_HZ]).
 * The heartbeat mixed into a shared note is a known signal, so it can be
 * found again after re-encoding: band-pass, normalised
 * cross-correlation with the lub template, adaptive peak picking, a dub check
 * (which rejects voice energy), and an RR range check. After the band-pass
 * nothing above ~100 Hz is left, so the correlation runs at 2 kHz (same
 * result, 16× less work). Pure Kotlin for JVM tests.
 */
object BeatRecoveryCore {
    const val INPUT_RATE = 8_000
    private const val WORK_RATE = 2_000
    private const val DECIMATE = INPUT_RATE / WORK_RATE
    private const val MIN_SPACING_MS = 333L
    private const val MAX_RR_MS = 1_500L
    private const val THRESHOLD_WINDOW_MS = 4_000L
    private const val MAD_K = 4.0
    private const val DUB_TOLERANCE_MS = 40L
    private const val DUB_MAD_K = 2.0
    private const val MIN_BEATS = 10
    private const val COLUMN_MS = 200L
    const val BAND_LOW_HZ = 40.0
    /**
     * DEVIATION from the spec's 220 Hz (measured 2026-11-18, BeatRecoveryTest
     * fixtures): at 40–220 Hz voice fundamentals correlate with the lub as well
     * as the beats do (beats p10 0.39, voice-only max 0.46). At 40–100 Hz beats
     * score p10 0.77 vs voice-only max 0.49, and the 55/70 Hz fundamentals of
     * every Heartnote preset are kept.
     */
    const val BAND_HIGH_HZ = 100.0
    private const val MIN_LUB_NCC = 0.6f
    private const val MIN_DUB_NCC = 0.4f
    private const val RELAXED_LUB_NCC = 0.3f
    private const val RELAXED_DUB_NCC = 0.25f

    data class Result(val beatsMs: List<Long>, val dotColumns: List<Float>)

    fun detect(pcm8k: FloatArray, durationMs: Long): Result? {
        if (pcm8k.size < INPUT_RATE * 3) return null
        val x = decimate(bandPass(pcm8k, INPUT_RATE))
        val lub = template(HeartbeatSynth.lubTemplate())
        val dub = template(dubTemplate())
        val lubNcc = ncc(x, lub)
        val dubNcc = ncc(x, dub)

        val lubThreshold = adaptiveThreshold(lubNcc, MAD_K, MIN_LUB_NCC)
        val dubThreshold = adaptiveThreshold(dubNcc, DUB_MAD_K, MIN_DUB_NCC)
        val candidates = pickPeaks(lubNcc, lubThreshold)
        if (candidates.size < MIN_BEATS) return null
        // NCC lag = template start = beat onset (template and signal share the same filters).

        // Dub check, using each candidate's interval to the next one as RR.
        val medianRr = candidates.zipWithNext { a, b -> b - a }.sorted().let { it[it.size / 2] }
        val beats = candidates.filterIndexed { i, t ->
            val rr = if (i + 1 < candidates.size) candidates[i + 1] - t else medianRr
            val d = HeartbeatSynth.dubDelayMs(rr.coerceIn(MIN_SPACING_MS, MAX_RR_MS))
            hasPeak(dubNcc, dubThreshold, t + d - DUB_TOLERANCE_MS, t + d + DUB_TOLERANCE_MS)
        }

        // Rhythm-guided second pass: a voice in the band (deep voices) hides some beats
        // below the floor; search only where the rhythm says a beat must be.
        val filled = if (beats.size >= 4) fillGaps(beats, lubNcc, dubNcc) else beats

        // RR range: keep beats whose interval to a neighbour is 333–1500 ms.
        val kept = filled.filterIndexed { i, t ->
            val prev = if (i > 0) t - filled[i - 1] else null
            val next = if (i + 1 < filled.size) filled[i + 1] - t else null
            listOfNotNull(prev, next).any { it in MIN_SPACING_MS..MAX_RR_MS }
        }.filter { it in 0..durationMs }
        if (kept.size < MIN_BEATS) return null
        val rr = kept.zipWithNext { a, b -> b - a }.sorted()
        val bpm = 60_000.0 / rr[rr.size / 2]
        if (bpm !in 40.0..180.0) return null

        val columns = ((durationMs + COLUMN_MS - 1) / COLUMN_MS).toInt()
        val dot = MutableList(columns) { 0.25f }
        kept.forEach { b -> dot[(b / COLUMN_MS).toInt().coerceIn(0, columns - 1)] = 1f }
        return Result(kept, dot)
    }

    /**
     * For every gap longer than 1.5 × the median interval, look for the missing
     * beats within ±20% of an interval around their expected times, accepting a
     * local lub peak ≥ [RELAXED_LUB_NCC] that is followed by a dub ≥ [RELAXED_DUB_NCC].
     */
    private fun fillGaps(beats: List<Long>, lubNcc: FloatArray, dubNcc: FloatArray): List<Long> {
        val rr = beats.zipWithNext { a, b -> b - a }.sorted().let { it[it.size / 2] }
        val out = ArrayList<Long>(beats)
        fun search(expected: Long): Long? {
            val half = (rr * 0.2).toLong()
            var best = -1
            var bestV = RELAXED_LUB_NCC
            val a = ((expected - half) * WORK_RATE / 1000).toInt().coerceAtLeast(1)
            val b = ((expected + half) * WORK_RATE / 1000).toInt().coerceAtMost(lubNcc.size - 2)
            for (i in a..b) {
                if (lubNcc[i] > bestV && lubNcc[i] >= lubNcc[i - 1] && lubNcc[i] >= lubNcc[i + 1]) {
                    best = i
                    bestV = lubNcc[i]
                }
            }
            if (best < 0) return null
            val t = best * 1000L / WORK_RATE
            val d = HeartbeatSynth.dubDelayMs(rr.coerceIn(MIN_SPACING_MS, MAX_RR_MS))
            val da = ((t + d - DUB_TOLERANCE_MS) * WORK_RATE / 1000).toInt().coerceAtLeast(0)
            val db = ((t + d + DUB_TOLERANCE_MS) * WORK_RATE / 1000).toInt().coerceAtMost(dubNcc.size - 1)
            return if ((da..db).any { dubNcc[it] > RELAXED_DUB_NCC }) t else null
        }
        for (k in 0 until beats.size - 1) {
            val gap = beats[k + 1] - beats[k]
            if (gap <= rr * 3 / 2) continue
            val missing = Math.round(gap.toDouble() / rr).toInt() - 1
            for (j in 1..missing) search(beats[k] + gap * j / (missing + 1))?.let { out += it }
        }
        // Before the first and after the last confident beat.
        var t = beats.first() - rr
        while (t > 0) { search(t)?.let { out += it }; t -= rr }
        t = beats.last() + rr
        while (t < lubNcc.size * 1000L / WORK_RATE) { search(t)?.let { out += it }; t += rr }
        return out.sorted().distinct()
    }

    // ---- signal steps ----

    /** 2nd-order Butterworth high-pass then low-pass, each run twice (4th order). */
    fun bandPass(x: FloatArray, rate: Int, lowHz: Double = BAND_LOW_HZ, highHz: Double = BAND_HIGH_HZ): FloatArray {
        var y = biquad(x, rate, lowHz, highPass = true)
        y = biquad(y, rate, lowHz, highPass = true)
        y = biquad(y, rate, highHz, highPass = false)
        return biquad(y, rate, highHz, highPass = false)
    }

    private fun biquad(x: FloatArray, rate: Int, fc: Double, highPass: Boolean): FloatArray {
        val w = 2 * PI * fc / rate
        val alpha = sin(w) / (2 * 0.7071)
        val c = cos(w)
        val a0 = 1 + alpha
        val b0 = (if (highPass) (1 + c) / 2 else (1 - c) / 2) / a0
        val b1 = (if (highPass) -(1 + c) else 1 - c) / a0
        val b2 = b0
        val a1 = -2 * c / a0
        val a2 = (1 - alpha) / a0
        val y = FloatArray(x.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val v = b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x[i].toDouble(); y2 = y1; y1 = v
            y[i] = v.toFloat()
        }
        return y
    }

    private fun decimate(x: FloatArray): FloatArray = FloatArray(x.size / DECIMATE) { x[it * DECIMATE] }

    /** Synth template (48 kHz) → 8 kHz → band-passed → 2 kHz, the same path as the signal. */
    private fun template(t48: FloatArray): FloatArray {
        val ratio = HeartbeatSynth.SAMPLE_RATE / INPUT_RATE
        val t8 = FloatArray(t48.size / ratio) { i -> (0 until ratio).sumOf { t48[i * ratio + it].toDouble() }.toFloat() / ratio }
        val padded = t8 + FloatArray(INPUT_RATE / 20) // let the filters ring out
        return decimate(bandPass(padded, INPUT_RATE))
    }

    private fun dubTemplate(): FloatArray {
        // The dub is the second sound of a single beat; isolate it from a one-beat render.
        val one = HeartbeatSynth.render(listOf(0L), 800)
        val start = HeartbeatSynth.msToSamples(HeartbeatSynth.dubDelayMs(800).toDouble())
        return one.copyOfRange(start, min(one.size, start + HeartbeatSynth.msToSamples(120.0)))
    }

    /** Normalised cross-correlation of [x] with template [h] at every lag. */
    fun ncc(x: FloatArray, h: FloatArray): FloatArray {
        val m = h.size
        if (x.size <= m) return FloatArray(0)
        var hNorm = 0.0
        for (v in h) hNorm += v * v
        hNorm = sqrt(hNorm)
        val out = FloatArray(x.size - m)
        var energy = 0.0
        for (k in 0 until m) energy += x[k] * x[k]
        for (t in out.indices) {
            var dot = 0.0
            for (k in 0 until m) dot += x[t + k] * h[k]
            out[t] = if (energy > 1e-12) (dot / (sqrt(energy) * hNorm)).toFloat() else 0f
            energy += x[t + m] * x[t + m] - x[t] * x[t]
            if (energy < 0) energy = 0.0
        }
        return out
    }

    /** max(floor, rolling median + k × MAD over 4 s windows (hop 0.5 s)), per sample. */
    private fun adaptiveThreshold(c: FloatArray, k: Double, floor: Float): FloatArray {
        val win = (THRESHOLD_WINDOW_MS * WORK_RATE / 1000).toInt()
        val hop = WORK_RATE / 2
        val out = FloatArray(c.size)
        var start = 0
        while (start < c.size) {
            val lo = max(0, start + hop / 2 - win / 2)
            val hi = min(c.size, lo + win)
            val slice = c.copyOfRange(lo, hi).sorted()
            val median = slice[slice.size / 2]
            val mad = slice.map { abs(it - median) }.sorted()[slice.size / 2]
            val th = max(floor, (median + k * mad).toFloat())
            for (i in start until min(c.size, start + hop)) out[i] = th
            start += hop
        }
        return out
    }

    /** Local maxima above threshold, at least 333 ms apart (taller wins). */
    private fun pickPeaks(c: FloatArray, th: FloatArray): List<Long> {
        val spacing = (MIN_SPACING_MS * WORK_RATE / 1000).toInt()
        val candidates = ArrayList<Int>()
        for (i in 1 until c.size - 1) if (c[i] > th[i] && c[i] >= c[i - 1] && c[i] > c[i + 1]) candidates += i
        val accepted = ArrayList<Int>()
        for (i in candidates.sortedByDescending { c[it] }) if (accepted.none { abs(it - i) < spacing }) accepted += i
        return accepted.sorted().map { (it * 1000L / WORK_RATE) }
    }

    private fun hasPeak(c: FloatArray, th: FloatArray, fromMs: Long, toMs: Long): Boolean {
        val a = (fromMs * WORK_RATE / 1000).toInt().coerceAtLeast(0)
        val b = (toMs * WORK_RATE / 1000).toInt().coerceAtMost(c.size - 1)
        for (i in a..b) if (c[i] > th[i]) return true
        return false
    }

    /** Linear-interpolation resampler with a simple anti-alias low-pass (decoder output → 8 kHz). */
    fun resampleTo8k(x: FloatArray, rate: Int): FloatArray {
        if (rate == INPUT_RATE) return x
        // Two one-pole passes at 3.4 kHz before decimating.
        val a = kotlin.math.exp(-2 * PI * 3_400.0 / rate)
        val y = FloatArray(x.size)
        var s1 = 0.0
        var s2 = 0.0
        for (i in x.indices) {
            s1 = (1 - a) * x[i] + a * s1
            s2 = (1 - a) * s1 + a * s2
            y[i] = s2.toFloat()
        }
        val n = (x.size.toLong() * INPUT_RATE / rate).toInt()
        return FloatArray(n) { i ->
            val pos = i.toDouble() * rate / INPUT_RATE
            val j = pos.toInt().coerceAtMost(y.size - 2)
            val f = (pos - j).toFloat()
            y[j] * (1 - f) + y[j + 1] * f
        }
    }
}
