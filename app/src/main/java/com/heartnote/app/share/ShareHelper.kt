package com.heartnote.app.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * F9 / TRD § EXPORT: share notes/<id>/heartnote.m4a through FileProvider with
 * ACTION_SEND. Only the audio file is shared; signal data never leaves the app.
 */
object ShareHelper {
    fun share(context: Context, audio: File, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", audio)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(audio.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}
