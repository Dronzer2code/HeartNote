package com.heartnote.app.share

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Save to phone": copies an exported file (WITH the payload track) to
 * Downloads/Heartnote so it can be attached on WhatsApp as a Document.
 * API 29+: MediaStore.Downloads (no permission). API 28: the public
 * Downloads folder with WRITE_EXTERNAL_STORAGE (maxSdkVersion 28), requested
 * only when the user saves.
 */
object DeviceSaver {
    const val FOLDER = "Heartnote"
    val DISPLAY_FOLDER = "Downloads/$FOLDER"

    val needsLegacyPermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** Heartnote_<title>_<yyyyMMdd_HHmm>.<ext>, sanitised for every file system. */
    fun fileName(title: String, extension: String, now: LocalDateTime = LocalDateTime.now()): String =
        "Heartnote_${sanitize(title)}_${now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))}.$extension"

    fun sanitize(title: String): String =
        title.trim()
            .replace(Regex("[^\\p{L}\\p{N}-]+"), "_")
            .trim('_')
            .take(40)
            .ifEmpty { "note" }

    /** Returns the saved file's display name. */
    suspend fun save(context: Context, source: File, name: String, mimeType: String): String = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) { "MediaStore insert failed" }
            try {
                resolver.openOutputStream(uri)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
            dir.mkdirs()
            val target = File(dir, name)
            source.copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mimeType), null)
        }
        name
    }
}
