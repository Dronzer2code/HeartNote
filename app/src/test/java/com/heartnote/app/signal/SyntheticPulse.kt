package com.heartnote.app.signal

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthetic SmartSpectra-like pulse trace: ~29.8 Hz samples with timing jitter,
 * a systolic wave per beat, a smaller dicrotic wave 330 ms later, baseline drift
 * and noise. Beat times are known exactly, so detection can be scored.
 */
object SyntheticPulse {
    data class Fixture(val trace: List<TimedValue>, val beatsMs: List<Long>, val bpm: Double)

    fun generate(
        startMs: Long = -5_000,
        endMs: Long = 45_000,
        bpm: Double = 75.0,
        rrJitterMs: Double = 25.0,
        noise: Double = 0.05,
        seed: Int = 7,
    ): Fixture {
        val rnd = Random(seed)
        val rr = 60_000.0 / bpm
        val beats = ArrayList<Long>()
        var t = startMs + rr / 3
        while (t < endMs + rr) {
            beats += t.toLong()
            t += rr + (rnd.nextDouble() * 2 - 1) * rrJitterMs
        }

        val samples = ArrayList<TimedValue>()
        val period = 1000.0 / 29.8
        var ts = startMs.toDouble()
        while (ts <= endMs) {
            val jittered = ts + (rnd.nextDouble() * 2 - 1) * 2.0
            var v = 0.0
            for (b in beats) {
                val dt = (jittered - b) / 1000.0
                if (dt in -0.3..0.9) {
                    v += gauss(dt, 0.0, 0.045)          // systolic peak
                    v += 0.35 * gauss(dt, 0.33, 0.06)   // dicrotic wave
                }
            }
            v += 0.4 * sin(2 * PI * jittered / 7_000.0) // breathing-scale drift
            v += (rnd.nextDouble() * 2 - 1) * noise
            samples += TimedValue(jittered.toLong(), v.toFloat())
            ts += period
        }
        return Fixture(samples, beats, bpm)
    }

    fun pulseRate(startMs: Long, endMs: Long, bpm: Double, stepMs: Long = 830): List<TimedValue> =
        (startMs..endMs step stepMs).map { TimedValue(it, bpm.toFloat()) }

    private fun gauss(x: Double, mu: Double, sigma: Double) =
        exp(-((x - mu) * (x - mu)) / (2 * sigma * sigma))
}
