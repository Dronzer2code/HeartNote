package com.heartnote.app.share

import java.io.File

/**
 * SHARE_AND_IMPORT.md § BEAT RECOVERY — used only when a received file has
 * no payload track. Step 5 implements the matched filter; until then every
 * payload-less file is refused.
 */
object BeatRecovery {
    const val SOURCE = "recovered"

    data class Recovered(val beatsMs: List<Long>, val dotColumns: List<Float>)

    fun recover(file: File, durationMs: Long): Recovered? = null
}
