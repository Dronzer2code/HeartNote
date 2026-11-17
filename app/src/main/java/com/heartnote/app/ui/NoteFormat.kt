package com.heartnote.app.ui

import android.content.Context
import com.heartnote.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** "Today • Nov 17, 2026", "Yesterday • …", else "Mon • Nov 16, 2026". */
fun formatNoteDate(context: Context, epochMs: Long, today: LocalDate = LocalDate.now()): String {
    val locale = Locale.getDefault()
    val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val day = when (date) {
        today -> context.getString(R.string.date_today)
        today.minusDays(1) -> context.getString(R.string.date_yesterday)
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    }
    val full = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
    return context.getString(R.string.date_line, day, full)
}

/** m:ss */
fun formatDuration(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
