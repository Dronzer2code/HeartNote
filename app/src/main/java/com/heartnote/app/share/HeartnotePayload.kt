package com.heartnote.app.share

import com.heartnote.app.data.NoteMeta
import com.heartnote.app.data.SignalsFile
import com.heartnote.app.data.SmileSegment
import com.heartnote.app.ui.playback.DotChartData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * SHARE_AND_IMPORT.md § PAYLOAD: gzip-compressed UTF-8 JSON in an
 * `application/x-heartnote` MP4 metadata track. Data minimisation (MUST):
 * only these fields — no bpTrace, pulse rate, breathing or expression scores.
 */
@Serializable
data class HeartnotePayload(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val title: String,
    val createdAt: String,
    val durationMs: Long,
    val beatSource: String,
    val beatsMs: List<Long>,
    val smiles: List<SmileSegment>,
    val dotColumns: List<Float>,
) {
    fun encode(): ByteArray {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(json.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)) }
        return bytes.toByteArray()
    }

    companion object {
        const val FORMAT = "heartnote"
        const val VERSION = 1
        const val MIME = "application/x-heartnote"
        private const val MAX_JSON_BYTES = 2 * 1024 * 1024

        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun from(meta: NoteMeta, signals: SignalsFile): HeartnotePayload = HeartnotePayload(
            title = meta.title,
            createdAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(meta.createdAt), ZoneId.systemDefault()).toString(),
            durationMs = signals.durationMs,
            beatSource = signals.beatSource ?: meta.beatSource,
            beatsMs = signals.beatsMs,
            smiles = signals.smiles,
            dotColumns = if (signals.dotColumns.isNotEmpty()) signals.dotColumns else DotChartData.payloadColumns(signals),
        )

        /** Null when the bytes are not a Heartnote payload this version understands. */
        fun decode(bytes: ByteArray): HeartnotePayload? = runCatching {
            val text = GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8_192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    require(out.size() <= MAX_JSON_BYTES) { "payload too large" }
                }
                out.toString(Charsets.UTF_8.name())
            }
            json.decodeFromString(serializer(), text)
        }.getOrNull()?.takeIf { it.format == FORMAT && it.version == VERSION && it.durationMs > 0 }
    }
}
