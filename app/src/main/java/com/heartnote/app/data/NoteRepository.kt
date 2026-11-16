package com.heartnote.app.data

import java.io.File
import java.util.UUID
import kotlinx.serialization.json.Json

/** File storage under filesDir/notes/<id>/ (TRD § DATA MODEL). */
class NoteRepository(filesDir: File) {
    private val json = Json { encodeDefaults = true }
    private val notesRoot = File(filesDir, "notes")

    fun createNote(): String {
        val id = UUID.randomUUID().toString()
        File(notesRoot, id).mkdirs()
        return id
    }

    fun noteDir(id: String): File = File(notesRoot, id)
    fun voiceFile(id: String): File = File(noteDir(id), "voice.wav")
    fun signalsFile(id: String): File = File(noteDir(id), "signals.json")

    fun writeSignals(id: String, signals: SignalsFile) {
        signalsFile(id).writeText(json.encodeToString(SignalsFile.serializer(), signals))
    }

    fun delete(id: String) {
        noteDir(id).deleteRecursively()
    }
}
