package com.heartnote.app.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heartnote.app.R
import com.heartnote.app.share.ShareHelper
import com.heartnote.app.ui.playback.ShareSheet

/**
 * Host for the one share sheet. Every share button calls
 * `ShareViewModel.open(meta)`; this composable shows the sheet and, when a
 * file is ready, opens the system chooser through ShareHelper.
 */
@Composable
fun NoteShareSheet(viewModel: ShareViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.share_chooser_title)

    LaunchedEffect(state.shareReady) {
        state.shareReady?.let {
            viewModel.onShared()
            ShareHelper.share(context, it.file, chooserTitle, it.mimeType)
        }
    }

    val meta = state.meta ?: return
    ShareSheet(
        received = meta.isReceived,
        video = state.video,
        busy = state.busy,
        onShareVideo = viewModel::shareVideo,
        onShareAudio = viewModel::shareAudio,
        onCancelVideo = viewModel::cancelVideo,
        onDismiss = viewModel::dismiss,
    )
}
