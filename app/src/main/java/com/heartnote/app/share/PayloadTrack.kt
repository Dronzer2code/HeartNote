package com.heartnote.app.share

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * SHARE_AND_IMPORT.md remux pass: copy every track of an MP4/M4A and add the
 * `application/x-heartnote` metadata track (MediaMuxer supports metadata
 * tracks from API 26; MediaExtractor reads them back). Any older payload
 * track in the input is replaced.
 */
object PayloadTrack {
    private const val DEFAULT_BUFFER = 2 * 1024 * 1024

    fun write(input: File, output: File, payload: ByteArray) {
        val partial = File(output.parentFile, output.name + ".part")
        partial.delete()
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(partial.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(input.path)
            val map = IntArray(extractor.trackCount) { -1 }
            var bufferSize = DEFAULT_BUFFER
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME) == HeartnotePayload.MIME) continue
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
                map[i] = muxer.addTrack(format)
                extractor.selectTrack(i)
            }
            val metaTrack = muxer.addTrack(MediaFormat().apply { setString(MediaFormat.KEY_MIME, HeartnotePayload.MIME) })
            muxer.start()

            val info = MediaCodec.BufferInfo()
            info.set(0, payload.size, 0, 0)
            muxer.writeSampleData(metaTrack, ByteBuffer.wrap(payload), info)

            val buffer = ByteBuffer.allocate(bufferSize)
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val track = map[extractor.sampleTrackIndex]
                if (track >= 0) {
                    val key = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                    info.set(0, size, extractor.sampleTime, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(track, buffer, info)
                }
                extractor.advance()
            }
            muxer.stop()
        } catch (e: Exception) {
            partial.delete()
            throw e
        } finally {
            extractor.release()
            runCatching { muxer.release() }
        }
        if (!partial.renameTo(output)) {
            partial.copyTo(output, overwrite = true)
            partial.delete()
        }
    }

    /** The payload sample, or null if the file has no Heartnote track (or isn't an MP4). */
    fun read(file: File): ByteArray? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == HeartnotePayload.MIME
            } ?: return null
            extractor.selectTrack(track)
            val buffer = ByteBuffer.allocate(DEFAULT_BUFFER)
            val size = extractor.readSampleData(buffer, 0)
            if (size <= 0) null else ByteArray(size).also { buffer.get(it, 0, size) }
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    /** Duration of the longest audio/video track in ms, or null. */
    fun durationMs(file: File): Long? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.path)
            (0 until extractor.trackCount)
                .map { extractor.getTrackFormat(it) }
                .filter { it.containsKey(MediaFormat.KEY_DURATION) }
                .maxOfOrNull { it.getLong(MediaFormat.KEY_DURATION) / 1000 }
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    fun hasAudio(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.path)
            (0 until extractor.trackCount).any {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
        } catch (e: Exception) {
            false
        } finally {
            extractor.release()
        }
    }
}
