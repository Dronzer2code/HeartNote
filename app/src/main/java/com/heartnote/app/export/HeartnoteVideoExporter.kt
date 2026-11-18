package com.heartnote.app.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import com.heartnote.app.share.PayloadTrack
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * F13 (SHARE_AND_IMPORT.md § VIDEO EXPORT), Option B: H.264 via MediaCodec
 * with a Surface input drawn with lockHardwareCanvas, presentation times
 * rewritten to frameIndex × 33,333 µs while draining, AAC copied from
 * heartnote.m4a, then the remux pass adds the payload track.
 * Option A (Media3 Transformer + CanvasOverlay) was checked with javap and
 * fits too; B was chosen for deterministic timing and no new dependencies.
 * Cancellable: cancel the calling coroutine.
 */
class HeartnoteVideoExporter(private val context: Context) {

    suspend fun export(
        title: String,
        beatsMs: List<Long>,
        durationMs: Long,
        audio: File,
        payload: ByteArray,
        out: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.Default) {
        val av = File(out.parentFile, out.name + ".av.part")
        try {
            encode(title, beatsMs, durationMs, audio, av, onProgress)
            coroutineContext.ensureActive()
            PayloadTrack.write(av, out, payload)
        } finally {
            av.delete()
        }
    }

    private suspend fun encode(
        title: String,
        beatsMs: List<Long>,
        durationMs: Long,
        audio: File,
        output: File,
        onProgress: (Float) -> Unit,
    ) {
        val frame = HeartnoteVideoFrame(context, title, HeartnoteVideoFrame.beats(beatsMs), durationMs)
        val frames = ((durationMs * HeartnoteVideoFrame.FPS + 999) / 1000).toInt().coerceAtLeast(1)

        val format = MediaFormat.createVideoFormat(MIME, HeartnoteVideoFrame.WIDTH, HeartnoteVideoFrame.HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, HeartnoteVideoFrame.FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            // No B-frames: Baseline profile, and max-bframes 0 where supported.
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
        }
        val encoder = MediaCodec.createEncoderByType(MIME)
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var surface: android.view.Surface? = null
        try {
            extractor.setDataSource(audio.path)
            val audioIndex = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            val audioFormat = extractor.getTrackFormat(audioIndex)
            extractor.selectTrack(audioIndex)

            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = encoder.createInputSurface()
            encoder.start()

            val state = MuxState()
            for (f in 0 until frames) {
                coroutineContext.ensureActive()
                val canvas = surface.lockHardwareCanvas()
                try {
                    frame.draw(canvas, f * 1000L / HeartnoteVideoFrame.FPS)
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
                drain(encoder, muxer, audioFormat, state, endOfStream = false)
                if (f % 15 == 0) onProgress(f.toFloat() / frames * 0.95f)
            }
            encoder.signalEndOfInputStream()
            drain(encoder, muxer, audioFormat, state, endOfStream = true)

            // Audio: copy the AAC samples of heartnote.m4a as they are.
            val buffer = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                muxer.writeSampleData(state.audioTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
            onProgress(1f)
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
            surface?.release()
            extractor.release()
            runCatching { muxer.release() }
        }
    }

    private class MuxState {
        var videoTrack = -1
        var audioTrack = -1
        var written = 0L
    }

    private fun drain(encoder: MediaCodec, muxer: MediaMuxer, audioFormat: MediaFormat, s: MuxState, endOfStream: Boolean) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val index = encoder.dequeueOutputBuffer(info, if (endOfStream) 10_000L else 0L)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    s.videoTrack = muxer.addTrack(encoder.outputFormat)
                    s.audioTrack = muxer.addTrack(audioFormat)
                    muxer.start()
                }
                index >= 0 -> {
                    val data = encoder.getOutputBuffer(index)!!
                    val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!config && info.size > 0 && s.videoTrack >= 0) {
                        // Surface frames carry wall-clock times; use exact 30 fps times instead.
                        info.presentationTimeUs = s.written * FRAME_US
                        data.position(info.offset)
                        data.limit(info.offset + info.size)
                        muxer.writeSampleData(s.videoTrack, data, info)
                        s.written++
                    }
                    encoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    companion object {
        private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        /**
         * 1.8 Mbps (spec: 2 Mbps) keeps a 60 s note under the 16 MB target with
         * headroom for encoder bitrate overshoot (2 Mbps + 128 kbps is 15.96 MB exactly).
         */
        private const val VIDEO_BIT_RATE = 1_800_000
        private const val FRAME_US = 1_000_000L / HeartnoteVideoFrame.FPS
    }
}
