package com.heartnote.app.share

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder

/**
 * SHARE_AND_IMPORT.md § BEAT RECOVERY — used only when a received file has
 * no payload track (e.g. WhatsApp re-encoded it as media). Step 1 here:
 * decode the audio track to mono PCM and bring it to 8 kHz; steps 2–8 are in
 * [BeatRecoveryCore].
 */
object BeatRecovery {
    const val SOURCE = "recovered"
    private const val MAX_SECONDS = 10 * 60
    private const val TIMEOUT_US = 10_000L

    data class Recovered(val beatsMs: List<Long>, val dotColumns: List<Float>)

    /** Null when no heartbeat Heartnote can read was found (import is then refused). */
    fun recover(file: File, durationMs: Long): Recovered? {
        val decoded = runCatching { decodeMono(file) }.getOrNull() ?: return null
        val (pcm, rate) = decoded
        val pcm8k = BeatRecoveryCore.resampleTo8k(pcm, rate)
        val length = if (durationMs > 0) durationMs else pcm8k.size * 1000L / BeatRecoveryCore.INPUT_RATE
        return BeatRecoveryCore.detect(pcm8k, length)?.let { Recovered(it.beatsMs, it.dotColumns) }
    }

    private fun decodeMono(file: File): Pair<FloatArray, Int>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var isFloat = false
            val out = FloatGrowable(rate * 60)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buf = decoder.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = decoder.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIndex >= 0 -> {
                        val buf = decoder.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        if (isFloat) {
                            val fb = buf.asFloatBuffer()
                            while (fb.remaining() >= channels) {
                                var sum = 0f
                                repeat(channels) { sum += fb.get() }
                                out.add(sum / channels)
                            }
                        } else {
                            val sb = buf.asShortBuffer()
                            while (sb.remaining() >= channels) {
                                var sum = 0f
                                repeat(channels) { sum += sb.get() / 32_768f }
                                out.add(sum / channels)
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        if (out.size > rate * MAX_SECONDS) break
                    }
                }
            }
            return out.toArray() to rate
        } finally {
            codec?.let { runCatching { it.stop() }; it.release() }
            extractor.release()
        }
    }

    private class FloatGrowable(initial: Int) {
        private var data = FloatArray(initial.coerceAtLeast(1024))
        var size = 0
            private set

        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }

        fun toArray(): FloatArray = data.copyOf(size)
    }
}
