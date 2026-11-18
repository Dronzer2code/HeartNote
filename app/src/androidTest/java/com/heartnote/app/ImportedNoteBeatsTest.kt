package com.heartnote.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.heartnote.app.data.NoteMeta
import com.heartnote.app.data.NoteProcessor
import com.heartnote.app.data.NoteRepository
import com.heartnote.app.data.SignalsFile
import com.heartnote.app.share.ImportManager
import com.heartnote.app.share.ShareExports
import com.heartnote.app.ui.beat.BeatBus
import com.heartnote.app.ui.beat.BeatEvent
import com.heartnote.app.ui.beat.BeatKind
import com.heartnote.app.ui.playback.NotePlayer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fix 3 end-to-end on a device: a note exported by the app is imported, and
 * playing the imported note fires beat events on BeatBus (the events the
 * haptics subscriber vibrates on). Also proves the MediaMuxer metadata-track
 * round trip (payload written → read back) on this device.
 *
 *   ./gradlew :app:connectedDebugAndroidTest --tests '*ImportedNoteBeatsTest*'
 */
@RunWith(AndroidJUnit4::class)
class ImportedNoteBeatsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = NoteRepository(context.filesDir)
    private val beats = (300L until 7_500L step 800L).toList()
    private val durationMs = 8_000L

    private fun makeOwnNote(): NoteMeta = runBlocking {
        val id = repository.createNote()
        writeSilentWav(repository.voiceFile(id), durationMs)
        repository.writeSignals(id, SignalsFile(durationMs = durationMs, audioStartEpochMs = 0, beatSource = "trace", beatsMs = beats))
        val meta = NoteMeta(id, "Test", System.currentTimeMillis(), durationMs, beatCount = beats.size, beatSource = "trace")
        repository.writeMeta(meta)
        NoteProcessor(repository).render(id)
        meta
    }

    private fun importBack(shared: File): NoteMeta = runBlocking {
        val result = ImportManager(context, repository).import(Uri.fromFile(shared))
        assertTrue("import result $result", result is ImportManager.Result.Imported)
        repository.readMeta((result as ImportManager.Result.Imported).noteId)!!
    }

    @Test
    fun importedAudioKeepsBeatsFromThePayload() {
        val own = makeOwnNote()
        val shared = runBlocking { ShareExports(repository, context).audio(own) }
        val received = importBack(shared.file)
        val signals = repository.readSignals(received.id)
        assertEquals("payload track read back (not recovery)", "trace", signals.beatSource)
        assertEquals(beats, signals.beatsMs)
    }

    @Test
    fun importedVideoKeepsBeatsFromThePayload() {
        val own = makeOwnNote()
        val shared = runBlocking { ShareExports(repository, context).video(own) {} }
        val received = importBack(shared.file)
        assertEquals("trace", repository.readSignals(received.id).beatSource)
        assertTrue("video ≤ 16 MB per minute", shared.file.length() < 16L * 1024 * 1024 * durationMs / 60_000 + 1_000_000)
    }

    @Test
    fun playingAnImportedNoteFiresBeatEvents() = runBlocking {
        val own = makeOwnNote()
        val shared = ShareExports(repository, context).audio(own)
        val received = importBack(shared.file)
        val signals = repository.readSignals(received.id)

        val events = ArrayList<BeatEvent>()
        val scope = MainScope()
        scope.launch { BeatBus.events.collect { synchronized(events) { events += it } } }
        val player = withContext(Dispatchers.Main) {
            NotePlayer(context, scope).apply {
                load(repository.playbackFile(received), signals.beatsMs, signals.durationMs)
                play()
            }
        }
        delay(3_500)
        withContext(Dispatchers.Main) { player.release() }
        scope.cancel()

        val lubs = synchronized(events) { events.filter { it.kind == BeatKind.LUB && it.noteTimeMs != null } }
        // Beats at 0.3, 1.1, 1.9, 2.7 s (and maybe 3.5 s) are crossed within 3.5 s of playback.
        assertTrue("LUB events fired: ${lubs.map { it.noteTimeMs }}", lubs.size >= 3)
    }

    private fun writeSilentWav(file: File, durationMs: Long) {
        val samples = (durationMs * 48).toInt()
        val data = samples * 2
        val buf = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(48_000).putInt(96_000).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(data)
        file.writeBytes(buf.array())
    }
}
