package com.heartnote.app.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder
import kotlin.math.min

/** TRD § EXPORT: PCM16 mono → AAC-LC 128 kbps in an MPEG-4 (.m4a) container. */
object AacExporter {
    private const val MIME = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val BIT_RATE = 128_000
    private const val TIMEOUT_US = 10_000L

    fun export(pcm: ShortArray, sampleRate: Int, out: File) {
        val partial = File(out.parentFile, out.name + ".part")
        partial.delete()

        val format = MediaFormat.createAudioFormat(MIME, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MIME)
        val muxer = MediaMuxer(partial.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            encode(codec, muxer, pcm, sampleRate)
            muxer.stop()
        } finally {
            runCatching { codec.stop() }
            codec.release()
            runCatching { muxer.release() }
        }
        if (!partial.renameTo(out)) {
            partial.copyTo(out, overwrite = true)
            partial.delete()
        }
    }

    private fun encode(codec: MediaCodec, muxer: MediaMuxer, pcm: ShortArray, sampleRate: Int) {
        val info = MediaCodec.BufferInfo()
        var track = -1
        var inputPos = 0
        var inputDone = false
        while (true) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)!!
                    buffer.clear()
                    val count = min(buffer.remaining() / 2, pcm.size - inputPos)
                    val ptsUs = inputPos * 1_000_000L / sampleRate
                    if (count <= 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, inputPos, count)
                        codec.queueInputBuffer(inIndex, 0, count * 2, ptsUs, 0)
                        inputPos += count
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                }
                outIndex >= 0 -> {
                    val encoded = codec.getOutputBuffer(outIndex)!!
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!isConfig && info.size > 0 && track >= 0) {
                        encoded.position(info.offset)
                        encoded.limit(info.offset + info.size)
                        muxer.writeSampleData(track, encoded, info)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }
}
