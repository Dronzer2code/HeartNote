package com.heartnote.app.ui.beat

import com.heartnote.app.signal.SyntheticPulse
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBeatSchedulerTest {

    private class Run(val expected: List<Long>, val lubs: List<Long>, val dubs: List<Long>) {
        val onTime get() = expected.count { e -> lubs.any { abs(it - e) <= 50 } }
    }

    /**
     * Simulates measured SDK delivery: 1 s chunks whose median sample is 1.5 s
     * old on arrival (newest 1.0 s, oldest 2.0 s).
     */
    private fun simulate(delayMs: Long): Run {
        val epoch0 = 1_794_831_430_000L
        val fx = SyntheticPulse.generate(startMs = 0, endMs = 30_000, bpm = 72.0)
        var clock = epoch0
        val lubs = ArrayList<Long>()
        val dubs = ArrayList<Long>()
        val scheduler = LiveBeatScheduler(CoroutineScope(Dispatchers.Unconfined), { clock }, delayMs) { at, kind, _ ->
            if (kind == BeatKind.LUB) lubs += at else dubs += at
        }
        scheduler.onPulseRate((0L..30_000L step 830).map { (epoch0 + it) * 1000 to 72f })
        for (chunkEnd in 1_000L..30_000L step 1_000) {
            val chunk = fx.trace.filter { it.tMs in (chunkEnd - 1_000) until chunkEnd }
            clock = epoch0 + chunkEnd + 1_000
            scheduler.onTrace(chunk.map { (epoch0 + it.tMs) * 1000 to it.v })
        }
        val expected = fx.beatsMs.filter { it in 1_000..28_000 }.map { epoch0 + it + delayMs }
        return Run(expected, lubs, dubs)
    }

    @Test
    fun specDelayShowsEveryBeatExactlyOnce() {
        val run = simulate(LiveBeatScheduler.DISPLAY_DELAY_MS)
        run.expected.forEach { e ->
            // On its slot, or at most 450 ms late when it arrived after the slot.
            assertEquals("beat at $e shown once", 1, run.lubs.count { it - e in -50..450 })
        }
        assertEquals(run.lubs.size, run.dubs.size)
        run.lubs.zipWithNext { a, b -> b - a }.forEach { assertTrue("duplicate? interval $it", it > 300) }
        println("live 1800 ms: ${run.onTime}/${run.expected.size} beats exactly on their slot")
    }

    @Test
    fun longerDelayPutsEveryBeatOnItsSlot() {
        val run = simulate(2_300)
        println("live 2300 ms: ${run.onTime}/${run.expected.size} beats exactly on their slot")
        assertEquals(run.expected.size, run.onTime)
    }
}
