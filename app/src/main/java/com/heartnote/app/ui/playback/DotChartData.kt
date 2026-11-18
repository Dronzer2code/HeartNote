package com.heartnote.app.ui.playback

import com.heartnote.app.data.SignalsFile
import com.heartnote.app.signal.TimedValue
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * DESIGN.md § DATA FOR DOT CHARTS. Column value = peak-to-peak bpTrace
 * amplitude in the column's window, normalised to the chart's 95th
 * percentile column, clamped 0.15–1.0 (the spiky pattern of the reference).
 */
object DotChartData {
    const val LIBRARY_COLUMNS = 36
    const val ROW_MS = 8_000L
    const val ROW_COLUMNS = 40          // 200 ms per column
    const val COLUMN_MS = ROW_MS / ROW_COLUMNS
    private const val MIN_VALUE = 0.15f

    /** 36 columns spanning the whole note (Library card). */
    fun library(trace: List<TimedValue>, durationMs: Long): FloatArray =
        normalise(peakToPeak(trace, 0, max(durationMs, 1), LIBRARY_COLUMNS))

    /** One 40-column row per 8 s of note (Playback card), sharing one normalisation. */
    fun playbackRows(trace: List<TimedValue>, durationMs: Long): List<FloatArray> {
        val rows = rowCount(durationMs)
        val raw = peakToPeak(trace, 0, rows * ROW_MS, rows * ROW_COLUMNS)
        val all = normalise(raw)
        return List(rows) { r -> all.copyOfRange(r * ROW_COLUMNS, (r + 1) * ROW_COLUMNS) }
    }

    /** Library columns for a note: from its trace, or from received display columns. */
    fun library(signals: SignalsFile): FloatArray =
        if (signals.dotColumns.isNotEmpty()) {
            resample(signals.dotColumns, LIBRARY_COLUMNS)
        } else {
            library(signals.raw.bpTrace.map { TimedValue(it.tMs, it.v) }, signals.durationMs)
        }

    /** Playback rows for a note: from its trace, or from received display columns. */
    fun playbackRows(signals: SignalsFile): List<FloatArray> {
        if (signals.dotColumns.isEmpty()) {
            return playbackRows(signals.raw.bpTrace.map { TimedValue(it.tMs, it.v) }, signals.durationMs)
        }
        val rows = rowCount(signals.durationMs)
        return List(rows) { r ->
            FloatArray(ROW_COLUMNS) { c -> signals.dotColumns.getOrNull(r * ROW_COLUMNS + c)?.coerceIn(MIN_VALUE, 1f) ?: MIN_VALUE }
        }
    }

    /** 200 ms columns for the whole note (shared payload `dotColumns`). */
    fun payloadColumns(signals: SignalsFile): List<Float> {
        val count = max(1, ceil(signals.durationMs / COLUMN_MS.toDouble()).toInt())
        val trace = signals.raw.bpTrace.map { TimedValue(it.tMs, it.v) }
        val values = normalise(peakToPeak(trace, 0, count * COLUMN_MS, count))
        return values.map { (Math.round(it * 100) / 100f) }
    }

    private fun resample(values: List<Float>, columns: Int): FloatArray = FloatArray(columns) { c ->
        val from = c * values.size / columns
        val to = max(from + 1, (c + 1) * values.size / columns)
        values.subList(from, min(to, values.size)).maxOrNull()?.coerceIn(MIN_VALUE, 1f) ?: MIN_VALUE
    }

    fun rowCount(durationMs: Long): Int = max(1, ceil(durationMs / ROW_MS.toDouble()).toInt())

    /** Row and column holding [timeMs] in the Playback rows. */
    fun rowColumnOf(timeMs: Long): Pair<Int, Int> {
        val row = (timeMs / ROW_MS).toInt()
        val col = ((timeMs - row * ROW_MS) / COLUMN_MS).toInt().coerceIn(0, ROW_COLUMNS - 1)
        return row to col
    }

    /** Playback progress (0–1) within [row] for position [positionMs]. */
    fun rowProgress(row: Int, positionMs: Long): Float =
        ((positionMs - row * ROW_MS).toFloat() / ROW_MS).coerceIn(0f, 1f)

    private fun peakToPeak(trace: List<TimedValue>, fromMs: Long, toMs: Long, columns: Int): FloatArray {
        val lo = FloatArray(columns) { Float.POSITIVE_INFINITY }
        val hi = FloatArray(columns) { Float.NEGATIVE_INFINITY }
        val span = (toMs - fromMs).toDouble()
        for (s in trace) {
            if (s.tMs < fromMs || s.tMs >= toMs) continue
            val c = ((s.tMs - fromMs) / span * columns).toInt().coerceIn(0, columns - 1)
            lo[c] = min(lo[c], s.v)
            hi[c] = max(hi[c], s.v)
        }
        return FloatArray(columns) { if (hi[it] >= lo[it]) hi[it] - lo[it] else 0f }
    }

    private fun normalise(values: FloatArray): FloatArray {
        val sorted = values.filter { it > 0f }.sorted()
        if (sorted.isEmpty()) return FloatArray(values.size) { MIN_VALUE }
        val p95 = sorted[((sorted.size - 1) * 0.95).toInt()].takeIf { it > 0f } ?: 1f
        return FloatArray(values.size) { (values[it] / p95).coerceIn(MIN_VALUE, 1f) }
    }
}
