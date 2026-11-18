package com.heartnote.app.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * F9: share a note file through FileProvider with ACTION_SEND. Only the
 * audio/video file is shared; it carries the beat-timing payload
 * (SHARE_AND_IMPORT.md § PAYLOAD) but never raw signals.
 */
object ShareHelper {
    fun share(context: Context, audio: File, chooserTitle: String, mimeType: String = "audio/mp4") {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", audio)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(audio.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}
