package com.heartnote.app.ui.playback

import com.heartnote.app.signal.SyntheticPulse
import com.heartnote.app.signal.TimedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DotChartDataTest {
    private val trace = SyntheticPulse.generate(startMs = -2_000, endMs = 20_000).trace

    @Test
    fun libraryHas36ColumnsWithinRange() {
        val cols = DotChartData.library(trace, 20_000)
        assertEquals(36, cols.size)
        assertTrue(cols.all { it in 0.15f..1f })
        assertTrue("some columns reach the top", cols.count { it >= 0.99f } >= 1)
    }

    @Test
    fun playbackRowsCover8SecondsEach() {
        val rows = DotChartData.playbackRows(trace, 20_000)
        assertEquals(3, rows.size) // 0–8, 8–16, 16–24 s
        assertTrue(rows.all { it.size == 40 })
        // The last row is past the end of the note from 20 s: those columns sit at the floor.
        assertTrue(rows[2].takeLast(15).all { it == 0.15f })
    }

    @Test
    fun beatColumnsAreTallerThanQuietOnes() {
        // A pulse every 1 s; column 0 of each second holds the beat, others are flat.
        val spiky = (0 until 8_000 step 33).map { t -> TimedValue(t.toLong(), if (t % 1_000 < 66) 1f else 0.1f + (t % 7) * 0.001f) }
        val row = DotChartData.playbackRows(spiky, 8_000)[0]
        assertTrue(row[0] > 0.9f)
        assertTrue(row[2] <= 0.2f)
    }

    @Test
    fun rowAndColumnOfTime() {
        assertEquals(0 to 0, DotChartData.rowColumnOf(0))
        assertEquals(1 to 5, DotChartData.rowColumnOf(9_050))
        assertEquals(0.5f, DotChartData.rowProgress(1, 12_000), 1e-6f)
    }
}
