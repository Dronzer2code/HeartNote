package com.heartnote.app.ui.playback

import com.heartnote.app.ui.beat.BeatEvent
import com.heartnote.app.ui.beat.BeatKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatCursorTest {
    private val beats = listOf(500L, 1_300L, 2_100L, 2_900L)

    private fun playTo(cursor: BeatCursor, toMs: Long, stepMs: Long = 10): List<BeatEvent> {
        val out = ArrayList<BeatEvent>()
        var t = 0L
        while (t <= toMs) {
            cursor.advance(t) { out += it }
            t += stepMs
        }
        return out
    }

    @Test
    fun everyBeatFiresLubThenDubWithItsNoteTime() {
        val events = playTo(BeatCursor(beats).apply { reset(0) }, 3_500)
        assertEquals(8, events.size)
        assertEquals(beats, events.filter { it.kind == BeatKind.LUB }.map { it.noteTimeMs })
        // Haptics only vibrate for events with a note time: every playback event has one.
        assertTrue(events.all { it.noteTimeMs != null })
        // Each LUB carries its dub gap (0.3 × 800 = 240 ms).
        assertTrue(events.filter { it.kind == BeatKind.LUB }.all { it.dubDelayMs == 240L })
    }

    @Test
    fun seekingBackReplaysAndForwardSkips() {
        val cursor = BeatCursor(beats).apply { reset(0) }
        val first = ArrayList<BeatEvent>()
        cursor.advance(1_000) { first += it }
        assertEquals(2, first.size) // lub + dub of the 500 ms beat
        val replay = ArrayList<BeatEvent>()
        cursor.advance(0) { replay += it }
        cursor.advance(600) { replay += it }
        assertEquals(1, replay.size) // the 500 ms lub again
        cursor.reset(2_000)
        val after = ArrayList<BeatEvent>()
        cursor.advance(2_200) { after += it }
        assertEquals(listOf(2_100L), after.map { it.noteTimeMs })
    }

    @Test
    fun receivedNotePayloadBeatsFireLikeOwnNotes() {
        // Beats as an imported note stores them (from the payload).
        val received = listOf(812L, 1_640L, 2_455L, 3_270L, 4_090L)
        val events = playTo(BeatCursor(received).apply { reset(0) }, 5_000)
        assertEquals(received.size, events.count { it.kind == BeatKind.LUB })
    }
}
