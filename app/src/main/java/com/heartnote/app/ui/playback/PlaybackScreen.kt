package com.heartnote.app.ui.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartnote.app.R
import com.heartnote.app.signal.BeatSource

/** F7 playback. Interim layout; restyled to the reference in Phase D. */
@Composable
fun PlaybackScreen(viewModel: PlaybackViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (state.loadFailed) {
            Text(stringResource(R.string.playback_load_failed))
            return@Column
        }
        val meta = state.meta ?: return@Column
        Text(meta.title, style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(
                if (BeatSource.fromKey(meta.beatSource) == BeatSource.TRACE) R.string.beat_label_trace
                else R.string.beat_label_rate,
            ),
        )
        Text("${formatMs(state.player.positionMs)} / ${formatMs(meta.durationMs)}")
        Button(onClick = viewModel::togglePlay) {
            Text(stringResource(if (state.player.isPlaying) R.string.playback_pause else R.string.playback_play))
        }
        if (!state.vibrationAvailable) Text(stringResource(R.string.playback_vibration_off))
        if (state.bluetoothOutput) Text(stringResource(R.string.playback_bluetooth_hint))
    }
}

fun formatMs(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
