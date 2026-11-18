package com.heartnote.app.share

import com.heartnote.app.data.NoteMeta
import com.heartnote.app.data.RawSample
import com.heartnote.app.data.RawSignals
import com.heartnote.app.data.SignalsFile
import com.heartnote.app.data.SmileSegment
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartnotePayloadTest {
    private val signals = SignalsFile(
        durationMs = 42_000,
        audioStartEpochMs = 1_794_832_863_878,
        beatSource = "trace",
        beatsMs = listOf(812, 1_640, 2_455),
        smiles = listOf(SmileSegment(12_000, 14_500)),
        raw = RawSignals(
            bpTrace = (0 until 42_000 step 33).map { RawSample(it.toLong(), if (it % 800 < 66) 1f else 0.1f) },
            pulseRate = listOf(RawSample(1_000, 74f, 0.9f)),
        ),
    )
    private val meta = NoteMeta("id", "Ma's birthday", 1_794_832_863_878, 42_000, beatCount = 3, beatSource = "trace")

    @Test
    fun roundTripsThroughGzip() {
        val payload = HeartnotePayload.from(meta, signals)
        val decoded = HeartnotePayload.decode(payload.encode())
        assertEquals(payload, decoded)
        assertEquals(210, decoded!!.dotColumns.size) // 200 ms columns over 42 s
    }

    @Test
    fun containsOnlyTheSpecifiedFields() {
        val bytes = HeartnotePayload.from(meta, signals).encode()
        val text = GZIPInputStream(ByteArrayInputStream(bytes)).readBytes().toString(Charsets.UTF_8)
        val keys = Json.parseToJsonElement(text).jsonObject.keys
        assertEquals(
            setOf("format", "version", "title", "createdAt", "durationMs", "beatSource", "beatsMs", "smiles", "dotColumns"),
            keys,
        )
        listOf("bpTrace", "pulseRate", "breathing", "expressions", "raw").forEach {
            assertTrue("$it must not be shared", !text.contains("\"$it\""))
        }
    }

    @Test
    fun rejectsOtherData() {
        assertNull(HeartnotePayload.decode("not gzip".toByteArray()))
        val wrongFormat = HeartnotePayload.from(meta, signals).copy(format = "other").encode()
        assertNull(HeartnotePayload.decode(wrongFormat))
    }
}
