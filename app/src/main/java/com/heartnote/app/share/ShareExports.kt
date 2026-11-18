package com.heartnote.app.share

import com.heartnote.app.data.NoteMeta
import com.heartnote.app.data.NoteRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Files handed to the share sheet. Own notes get a fresh copy with the
 * payload track (title included, so it is rebuilt on rename); received
 * notes are re-shared only as the original received file.
 */
class ShareExports(private val repository: NoteRepository) {

    data class Shareable(val file: File, val mimeType: String)

    suspend fun audio(meta: NoteMeta): Shareable = withContext(Dispatchers.IO) {
        if (meta.isReceived) return@withContext received(meta)
        val out = File(repository.shareDir(meta.id), fileName(meta.title, "m4a"))
        val payload = HeartnotePayload.from(meta, repository.readSignals(meta.id)).encode()
        repository.shareDir(meta.id).listFiles()?.filter { it.extension == "m4a" && it != out }?.forEach { it.delete() }
        PayloadTrack.write(repository.audioFile(meta.id), out, payload)
        Shareable(out, "audio/mp4")
    }

    fun received(meta: NoteMeta): Shareable {
        val file = repository.playbackFile(meta)
        return Shareable(file, if (file.extension == "m4a") "audio/mp4" else "video/mp4")
    }

    companion object {
        /** "Heartnote for Mum.m4a" — what the recipient sees in the chat. */
        fun fileName(title: String, extension: String): String {
            val safe = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "").trim().take(40).ifEmpty { "you" }
            return "Heartnote for $safe.$extension"
        }
    }
}
