package com.heartnote.app.capture

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * TRD § AUDIO CAPTURE: AudioRecord, MIC, 48 kHz mono PCM16 → voice.wav.
 * [audioStartEpochMs] (first buffer read) anchors the shared timeline.
 */
class AudioCapture {
    @Volatile
    var audioStartEpochMs: Long = 0L
        private set

    @Volatile
    private var bytesWritten: Long = 0

    @Volatile
    private var stopping = false

    private var recorder: AudioRecord? = null
    private var writerJob: Job? = null

    val durationMs: Long get() = bytesWritten * 1000 / BYTES_PER_SECOND

    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked by RecordScreen before starting
    fun start(scope: CoroutineScope, outFile: File, onError: (Throwable) -> Unit) {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL, ENCODING,
            maxOf(minBuffer * 2, 16384),
        )
        recorder = rec
        stopping = false
        writerJob = scope.launch(Dispatchers.IO) {
            try {
                RandomAccessFile(outFile, "rw").use { raf ->
                    raf.setLength(0)
                    raf.write(ByteArray(WAV_HEADER_SIZE)) // placeholder, patched on stop
                    val buffer = ByteArray(8192)
                    rec.startRecording()
                    var first = true
                    while (!stopping) {
                        val read = rec.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            if (first) {
                                first = false
                                audioStartEpochMs = System.currentTimeMillis()
                            }
                            raf.write(buffer, 0, read)
                            bytesWritten += read
                        } else if (read < 0) {
                            break
                        }
                    }
                    writeWavHeader(raf, bytesWritten)
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    /** Stops recording, finalizes the WAV header, returns the duration in ms. */
    suspend fun stop(): Long {
        stopping = true
        recorder?.let { runCatching { it.stop() } } // unblocks the pending read()
        writerJob?.join()
        writerJob = null
        recorder?.release()
        recorder = null
        return durationMs
    }

    private fun writeWavHeader(raf: RandomAccessFile, dataBytes: Long) {
        val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt((36 + dataBytes).toInt())
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)            // fmt chunk size
        header.putShort(1)           // PCM
        header.putShort(1)           // mono
        header.putInt(SAMPLE_RATE)
        header.putInt(BYTES_PER_SECOND.toInt())
        header.putShort(2)           // block align
        header.putShort(16)          // bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataBytes.toInt())
        raf.seek(0)
        raf.write(header.array())
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        const val BYTES_PER_SECOND = SAMPLE_RATE * 2L
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val WAV_HEADER_SIZE = 44
    }
}
