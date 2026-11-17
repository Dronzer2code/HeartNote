package com.heartnote.app.audio

import com.heartnote.app.signal.BeatExtractor
import com.heartnote.app.signal.TimedValue
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Writes testdata/private/heartbeat-preview.wav (heartbeat only, from the
 * developer's private recording) for a listening check. Skipped when absent.
 */
class HeartbeatPreviewTest {
    @Test
    fun renderPrivatePreview() {
        val src = File("../testdata/private/signals.json")
        assumeTrue(src.exists())
        val root = Json.parseToJsonElement(src.readText()).jsonObject
        val raw = root["raw"]!!.jsonObject
        fun series(name: String) = raw[name]!!.jsonArray.map {
            val o = it.jsonObject
            TimedValue(o["tMs"]!!.jsonPrimitive.long, o["v"]!!.jsonPrimitive.float)
        }
        val duration = root["durationMs"]!!.jsonPrimitive.long
        val beats = BeatExtractor.extract(series("bpTrace"), series("pulseRate"), duration)
        val pcm = Mixer.mix(ShortArray(HeartbeatSynth.msToSamples(duration.toDouble())), HeartbeatSynth.render(beats.beatsMs, duration))
        writeWav(File("../testdata/private/heartbeat-preview.wav"), pcm)
    }

    private fun writeWav(file: File, pcm: ShortArray) {
        val data = pcm.size * 2
        val buf = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(HeartbeatSynth.SAMPLE_RATE).putInt(HeartbeatSynth.SAMPLE_RATE * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(data)
        pcm.forEach { buf.putShort(it) }
        file.writeBytes(buf.array())
    }
}
