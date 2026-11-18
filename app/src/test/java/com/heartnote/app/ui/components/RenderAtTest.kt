package com.heartnote.app.ui.components

import com.heartnote.app.ui.components.particles.ParticleEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RenderAtTest {
    private val beats = longArrayOf(300, 1_157, 2_014, 2_871, 3_728)

    private fun engine() = ParticleEngine(HeartModel(HeartPoints.get())).apply {
        widthPx = 720f
        heightPx = 720f
    }

    @Test
    fun sameTimeAndBeatsGiveTheSameFrame() {
        val a = engine()
        val b = engine()
        // b renders other frames first: history must not matter.
        b.prepareAt(5_000, beats)
        b.prepareAt(150, beats)
        a.prepareAt(2_100, beats)
        b.prepareAt(2_100, beats)
        assertArrayEquals(a.snapshot(), b.snapshot(), 0f)
    }

    @Test
    fun beatsChangeTheFrame() {
        val a = engine()
        val b = engine()
        a.prepareAt(2_070, beats)
        b.prepareAt(2_070, longArrayOf())
        assertFalse(a.snapshot().contentEquals(b.snapshot()))
    }
}
