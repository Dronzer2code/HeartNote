package com.heartnote.app.signal

import org.junit.Assert.assertEquals
import org.junit.Test

class SmileDetectorTest {
    private fun samples(fromMs: Long, toMs: Long, c: Float) =
        (fromMs..toMs step 100).map { TimedValue(it, c) }

    @Test
    fun keepsSmilesOfAtLeast500Ms() {
        val happy = samples(0, 1_000, 0.1f) + samples(1_100, 1_900, 0.8f) +
            samples(2_000, 5_000, 0.1f) + samples(5_100, 5_300, 0.9f) + samples(5_400, 9_000, 0.1f)
        val smiles = SmileDetector.detect(happy, 10_000)
        assertEquals(listOf(SmileDetector.Segment(1_100, 1_900)), smiles)
    }

    @Test
    fun mergesSmilesLessThanOneSecondApart() {
        val happy = samples(1_000, 1_400, 0.7f) + samples(1_500, 1_900, 0.2f) + samples(2_000, 2_400, 0.7f)
        assertEquals(listOf(SmileDetector.Segment(1_000, 2_400)), SmileDetector.detect(happy, 10_000))
    }

    @Test
    fun clampsToTheNote() {
        val happy = samples(-800, 600, 0.9f)
        assertEquals(listOf(SmileDetector.Segment(0, 600)), SmileDetector.detect(happy, 10_000))
    }
}
