package com.heartnote.app.data

import java.io.File
import java.util.UUID
import kotlinx.serialization.json.Json

/** File storage under filesDir/notes/<id>/ (TRD § DATA MODEL). */
class NoteRepository(filesDir: File) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val notesRoot = File(filesDir, "notes")

    fun createNote(): String {
        val id = UUID.randomUUID().toString()
        File(notesRoot, id).mkdirs()
        return id
    }

    fun noteDir(id: String): File = File(notesRoot, id)
    fun voiceFile(id: String): File = File(noteDir(id), "voice.wav")
    fun signalsFile(id: String): File = File(noteDir(id), "signals.json")
    fun metaFile(id: String): File = File(noteDir(id), "meta.json")
    fun audioFile(id: String): File = File(noteDir(id), "heartnote.m4a")

    fun writeSignals(id: String, signals: SignalsFile) {
        writeAtomically(signalsFile(id), json.encodeToString(SignalsFile.serializer(), signals))
    }

    fun readSignals(id: String): SignalsFile =
        json.decodeFromString(SignalsFile.serializer(), signalsFile(id).readText())

    fun writeMeta(meta: NoteMeta) {
        writeAtomically(metaFile(meta.id), json.encodeToString(NoteMeta.serializer(), meta))
    }

    fun readMeta(id: String): NoteMeta? = runCatching {
        json.decodeFromString(NoteMeta.serializer(), metaFile(id).readText())
    }.getOrNull()

    /** Finished notes (meta + exported audio), newest first. */
    fun list(): List<NoteMeta> =
        (notesRoot.listFiles() ?: emptyArray())
            .filter { it.isDirectory && File(it, "heartnote.m4a").exists() }
            .mapNotNull { readMeta(it.name) }
            .sortedByDescending { it.createdAt }

    /**
     * Notes whose processing never finished (app killed during Processing, or
     * the Failed screen was left): raw files are kept for retry (PROCESS_FLOW).
     */
    fun unfinished(): List<String> =
        (notesRoot.listFiles() ?: emptyArray())
            .filter {
                it.isDirectory &&
                    File(it, "voice.wav").exists() &&
                    File(it, "signals.json").exists() &&
                    !File(it, "heartnote.m4a").exists()
            }
            .map { it.name }

    fun rename(id: String, title: String): NoteMeta? {
        val meta = readMeta(id) ?: return null
        val renamed = meta.copy(title = title.trim().ifEmpty { meta.title })
        writeMeta(renamed)
        return renamed
    }

    /** TRD § DATA MODEL: MUST recursively delete notes/<id>/. */
    fun delete(id: String) {
        noteDir(id).deleteRecursively()
    }

    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }
}
