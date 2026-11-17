package com.heartnote.app.ui.components

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartPointsTest {
    private val heart = HeartPoints.generate()
    private val n = heart.xyz.size / 3
    private fun x(i: Int) = heart.xyz[i * 3]
    private fun y(i: Int) = heart.xyz[i * 3 + 1]

    @Test
    fun has2600PointsScaledToLargestDimension2() {
        assertEquals(2_600, n)
        val extents = (0 until 3).map { a -> (0 until n).maxOf { heart.xyz[it * 3 + a] } - (0 until n).minOf { heart.xyz[it * 3 + a] } }
        assertEquals(2f, extents.max(), 1e-4f)
        assertEquals(extents[1], heart.heightUnits, 1e-4f)
    }

    @Test
    fun hasACleftAtTheTopAndAPointAtTheBottom() {
        val topCentre = (0 until n).filter { abs(x(it)) < 0.08f }.maxOf { y(it) }
        val topLobe = (0 until n).filter { abs(abs(x(it)) - 0.45f) < 0.08f }.maxOf { y(it) }
        // The DESIGN.md surface has a shallow cleft (~0.13 units on a 2-unit heart).
        assertTrue("cleft $topCentre < lobe $topLobe", topCentre < topLobe - 0.08f)
        val bottom = (0 until n).minBy { y(it) }
        assertTrue("tip near the centre line, x=${x(bottom)}", abs(x(bottom)) < 0.15f)
    }
}
