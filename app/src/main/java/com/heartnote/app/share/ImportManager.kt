package com.heartnote.app.share

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.heartnote.app.R
import com.heartnote.app.data.NoteMeta
import com.heartnote.app.data.NoteRepository
import com.heartnote.app.data.SignalsFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * F14 import pipeline (SHARE_AND_IMPORT.md § IMPORT). Always started by an
 * explicit user action: the picker, the share target or "Open with".
 */
class ImportManager(private val context: Context, private val repository: NoteRepository) {

    sealed interface Result {
        data class Imported(val noteId: String) : Result
        data object NotAHeartnote : Result
        data object Failed : Result
    }

    suspend fun import(uri: Uri): Result = withContext(Dispatchers.IO) {
        val id = repository.createNote()
        runCatching {
            // 1. Copy the incoming file into the note folder.
            val name = "received." + extensionFor(uri)
            val file = File(repository.noteDir(id), name)
            context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            if (!PayloadTrack.hasAudio(file)) return@runCatching Result.NotAHeartnote

            // 2. Payload track, else beat recovery from the audio.
            val payload = PayloadTrack.read(file)?.let { HeartnotePayload.decode(it) }
            val durationMs = PayloadTrack.durationMs(file) ?: payload?.durationMs ?: 0L
            val beats = payload?.let { BeatsFound(it.beatsMs, it.beatSource, it.smiles, it.dotColumns, it.title) }
                ?: BeatRecovery.recover(file, durationMs)?.let {
                    BeatsFound(it.beatsMs, BeatRecovery.SOURCE, emptyList(), it.dotColumns, null)
                }
                ?: return@runCatching Result.NotAHeartnote

            // 3. signals.json (beats, smiles, display columns — no raw signals) + meta.json.
            val duration = maxOf(durationMs, payload?.durationMs ?: 0L)
            repository.writeSignals(
                id,
                SignalsFile(
                    durationMs = duration,
                    audioStartEpochMs = 0,
                    beatSource = beats.source,
                    beatsMs = beats.beatsMs.filter { it in 0..duration },
                    smiles = beats.smiles,
                    dotColumns = beats.dotColumns,
                ),
            )
            repository.writeMeta(
                NoteMeta(
                    id = id,
                    title = beats.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.import_default_title),
                    createdAt = System.currentTimeMillis(),
                    durationMs = duration,
                    beatCount = beats.beatsMs.size,
                    beatSource = beats.source,
                    smileCount = beats.smiles.size,
                    origin = NoteMeta.ORIGIN_RECEIVED,
                    receivedFile = name,
                ),
            )
            Result.Imported(id)
        }.getOrElse { Result.Failed }.also { if (it !is Result.Imported) repository.delete(id) }
    }

    private data class BeatsFound(
        val beatsMs: List<Long>,
        val source: String,
        val smiles: List<com.heartnote.app.data.SmileSegment>,
        val dotColumns: List<Float>,
        val title: String?,
    )

    private fun extensionFor(uri: Uri): String {
        val type = context.contentResolver.getType(uri).orEmpty()
        val displayName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        val fromName = displayName.substringAfterLast('.', "").lowercase()
        return when {
            fromName in setOf("mp4", "m4a", "mov", "3gp", "aac") -> fromName
            type.startsWith("video/") -> "mp4"
            type.startsWith("audio/") -> "m4a"
            else -> "mp4"
        }
    }
}

/**
 * Share-target / "Open with" URIs wait here until the Library (after consent)
 * picks them up; nothing is imported before that (MUST NOT auto-import).
 */
object ImportRequests {
    private val _pending = MutableStateFlow<Uri?>(null)
    val pending: StateFlow<Uri?> = _pending

    fun offer(uri: Uri) {
        _pending.value = uri
    }

    fun take(): Uri? = _pending.value.also { _pending.value = null }
}
