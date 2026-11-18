package com.heartnote.app.ui.share

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heartnote.app.R
import com.heartnote.app.share.DeviceSaver
import com.heartnote.app.share.ShareHelper
import com.heartnote.app.ui.playback.ShareSheet
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnDims
import com.heartnote.app.ui.theme.HnType

/**
 * Host for the one share sheet. Every share button calls
 * `ShareViewModel.open(meta)`; this composable shows the sheet, opens the
 * system chooser through ShareHelper, requests the legacy storage permission
 * (API 28 only, at the moment of saving) and shows the "Saved" instructions.
 */
@Composable
fun NoteShareSheet(viewModel: ShareViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.share_chooser_title)
    var pendingSave by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingSave
        pendingSave = null
        if (granted) action?.invoke() else viewModel.onSaveDenied()
    }
    fun withStorage(action: () -> Unit) {
        val needs = DeviceSaver.needsLegacyPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needs) {
            pendingSave = action
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            action()
        }
    }

    LaunchedEffect(state.shareReady) {
        state.shareReady?.let {
            viewModel.onShared()
            ShareHelper.share(context, it.file, chooserTitle, it.mimeType)
        }
    }

    state.savedName?.let { name ->
        SavedSheet(name, onDone = viewModel::closeSaved)
        return
    }
    val meta = state.meta ?: return
    ShareSheet(
        received = meta.isReceived,
        video = state.video,
        busy = state.busy,
        error = state.error,
        onShareVideo = viewModel::shareVideo,
        onShareAudio = viewModel::shareAudio,
        onSaveVideo = { withStorage(viewModel::saveVideo) },
        onSaveAudio = { withStorage(viewModel::saveAudio) },
        onCancelVideo = viewModel::cancelVideo,
        onDismiss = viewModel::dismiss,
    )
}

/** After "Save to phone": where the file is and how to send it as a WhatsApp Document. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedSheet(fileName: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val whatsApp = remember { ShareHelper.isWhatsAppInstalled(context) }
    ModalBottomSheet(onDismissRequest = onDone, containerColor = HnColors.CardWhite) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(stringResource(R.string.saved_title), style = HnType.cardTitle, color = HnColors.InkBlack)
            Text(fileName, style = HnType.caption, color = HnColors.InkGrey, modifier = Modifier.padding(top = 4.dp))
            Text(
                stringResource(R.string.saved_steps),
                style = HnType.bodySecondary,
                color = HnColors.InkBlack,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (whatsApp) {
                Button(
                    onClick = { ShareHelper.openWhatsApp(context); onDone() },
                    shape = HnDims.pill,
                    colors = ButtonDefaults.buttonColors(containerColor = HnColors.InkBlack, contentColor = HnColors.CardWhite),
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.open_whatsapp), style = HnType.bodyPrimary) }
            }
            TextButton(
                onClick = onDone,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
                    .heightIn(min = HnDims.minTouch),
            ) { Text(stringResource(R.string.action_done), style = HnType.bodyPrimary, color = HnColors.InkGrey) }
        }
    }
}
