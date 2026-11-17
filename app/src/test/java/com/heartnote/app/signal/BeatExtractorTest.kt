package com.heartnote.app.signal

import java.io.File
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class BeatExtractorTest {

    private val durationMs = 40_000L

    @Test
    fun detectsEverySyntheticBeatWithin40Ms() {
        val fx = SyntheticPulse.generate()
        val result = BeatExtractor.extract(fx.trace, SyntheticPulse.pulseRate(-5_000, 45_000, fx.bpm), durationMs)

        val truth = fx.beatsMs.filter { it in 0..durationMs }
        assertEquals(BeatSource.TRACE, result.source)
        assertEquals(truth.size, result.beatsMs.size)
        truth.zip(result.beatsMs).forEach { (expected, actual) ->
            assertTrue("beat $actual vs $expected", abs(actual - expected) <= 40)
        }
    }

    @Test
    fun ignoresDicroticWave() {
        val fx = SyntheticPulse.generate(bpm = 60.0)
        val beats = BeatExtractor.detectBeats(fx.trace, SyntheticPulse.pulseRate(-5_000, 45_000, fx.bpm))
        val intervals = beats.zipWithNext { a, b -> b - a }
        assertTrue("min interval ${intervals.min()}", intervals.min() > 800)

        val result = BeatExtractor.extract(fx.trace, SyntheticPulse.pulseRate(-5_000, 45_000, fx.bpm), durationMs)
        assertEquals(fx.beatsMs.count { it in 0..durationMs }, result.beatsMs.size)
    }

    @Test
    fun dropsBeatsOutsideTheNoteButUsesPreRollContext() {
        val fx = SyntheticPulse.generate()
        val result = BeatExtractor.extract(fx.trace, emptyList(), durationMs)
        assertTrue(result.beatsMs.all { it in 0..durationMs })
        assertTrue(result.beatsMs.first() < 1_000)
    }

    @Test
    fun fillsTraceGapFromPulseRate() {
        val fx = SyntheticPulse.generate()
        val gapped = fx.trace.filterNot { it.tMs in 15_000..20_000 }
        val result = BeatExtractor.extract(gapped, SyntheticPulse.pulseRate(-5_000, 45_000, fx.bpm), durationMs)

        assertEquals(BeatSource.MIXED, result.source)
        val inGap = result.beatsMs.filter { it in 15_500..19_500 }
        assertTrue("filled ${inGap.size}", inGap.size in 4..6)
        result.beatsMs.zipWithNext { a, b -> b - a }.forEach {
            assertTrue("interval $it", it in BeatExtractor.MIN_RR_MS..BeatExtractor.MAX_RR_MS)
        }
    }

    @Test
    fun fallsBackToRateWithoutTrace() {
        val result = BeatExtractor.extract(emptyList(), SyntheticPulse.pulseRate(0, 40_000, 72.0), durationMs)
        assertEquals(BeatSource.RATE, result.source)
        assertTrue(abs(result.beatsMs.size - 48) <= 1) // 40 s × 72 BPM
    }

    @Test
    fun fallsBackToRateWhenTraceIsNoise() {
        val noise = SyntheticPulse.generate(noise = 0.0).trace.mapIndexed { i, s ->
            TimedValue(s.tMs, if (i % 2 == 0) 0f else 0.001f)
        }
        val result = BeatExtractor.extract(noise, SyntheticPulse.pulseRate(-5_000, 45_000, 70.0), durationMs)
        assertEquals(BeatSource.RATE, result.source)
    }

    @Test
    fun emptyInputsGiveNoBeats() {
        val result = BeatExtractor.extract(emptyList(), emptyList(), durationMs)
        assertTrue(result.beatsMs.isEmpty())
    }

    @Test
    fun medianFilterRemovesSingleJumps() {
        val values = listOf(80f, 81f, 101f, 82f, 80f, 60f, 81f)
        val smoothed = medianSmooth(values, 5)
        assertTrue(smoothed.all { it in 80f..82f })
        assertEquals(81f, trailingMedian(values, 5))
    }

    /** Developer recording in git-ignored testdata/private/; skipped when absent. */
    @Test
    fun privateRecordingAgreesWithPulseRate() {
        val file = File("../testdata/private/signals.json")
        assumeTrue(file.exists())
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val raw = root["raw"]!!.jsonObject
        fun series(name: String) = raw[name]!!.jsonArray.map {
            val o = it.jsonObject
            TimedValue(o["tMs"]!!.jsonPrimitive.long, o["v"]!!.jsonPrimitive.float)
        }
        val duration = root["durationMs"]!!.jsonPrimitive.long
        val pulse = series("pulseRate")
        val result = BeatExtractor.extract(series("bpTrace"), pulse, duration)

        val impliedBpm = result.beatsMs.size * 60_000.0 / duration
        val meanBpm = pulse.map { it.v }.average()
        println("private: ${result.beatsMs.size} beats, ${"%.1f".format(impliedBpm)} BPM vs ${"%.1f".format(meanBpm)}, ${result.source}")
        assertTrue(abs(impliedBpm - meanBpm) / meanBpm < 0.05)
        result.beatsMs.zipWithNext { a, b -> b - a }.forEach {
            assertTrue("interval $it", it in BeatExtractor.MIN_RR_MS..BeatExtractor.MAX_RR_MS)
        }
    }
}
