package com.heartnote.app.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.heartnote.app.R
import com.heartnote.app.ui.components.HeartnoteIcons
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnDims
import com.heartnote.app.ui.theme.HnType

sealed interface VideoExportState {
    data object Idle : VideoExportState
    data class Running(val progress: Float) : VideoExportState
    data object Failed : VideoExportState
}

/**
 * F9 revised (SHARE_AND_IMPORT.md § SHARE SHEET): one section per format,
 * each with Share (+ Save to phone, when [onSaveVideo]/[onSaveAudio] are
 * given). Received notes get one section for the original file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    received: Boolean,
    video: VideoExportState,
    busy: Boolean,
    onShareVideo: () -> Unit,
    onShareAudio: () -> Unit,
    onCancelVideo: () -> Unit,
    onDismiss: () -> Unit,
    onSaveVideo: (() -> Unit)? = null,
    onSaveAudio: (() -> Unit)? = null,
    @androidx.annotation.StringRes error: Int? = null,
) {
    val exporting = video is VideoExportState.Running
    ModalBottomSheet(
        onDismissRequest = { if (!exporting) onDismiss() },
        containerColor = HnColors.CardWhite,
        shape = RoundedCornerShape(topStart = HnDims.cardRadius, topEnd = HnDims.cardRadius),
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.share_sheet_title), style = HnType.cardTitle, color = HnColors.InkBlack)
            if (received) {
                FormatSection(
                    icon = HeartnoteIcons.Share,
                    title = stringResource(R.string.share_received),
                    body = stringResource(R.string.share_received_desc),
                    shareLabel = stringResource(R.string.share_action),
                    onShare = onShareAudio,
                    saveLabel = stringResource(R.string.save_to_phone),
                    onSave = onSaveAudio,
                    enabled = !busy,
                )
            } else {
                FormatSection(
                    icon = HeartnoteIcons.Play,
                    title = stringResource(R.string.share_video),
                    body = stringResource(R.string.share_video_desc),
                    shareLabel = stringResource(R.string.share_video_action),
                    onShare = onShareVideo,
                    saveLabel = stringResource(R.string.save_video_action),
                    onSave = onSaveVideo,
                    enabled = !exporting && !busy,
                )
                VideoProgress(video, onRetry = onShareVideo, onCancel = onCancelVideo)
                FormatSection(
                    icon = HeartnoteIcons.Mic,
                    title = stringResource(R.string.share_audio),
                    body = stringResource(R.string.share_audio_desc),
                    shareLabel = stringResource(R.string.share_audio_action),
                    onShare = onShareAudio,
                    saveLabel = stringResource(R.string.save_audio_action),
                    onSave = onSaveAudio,
                    enabled = !busy,
                )
            }
            error?.let { Text(stringResource(it), style = HnType.caption, color = HnColors.DotRed) }
            Text(stringResource(R.string.share_tip), style = HnType.caption, color = HnColors.InkGrey, modifier = Modifier.padding(top = 4.dp))
            Text(stringResource(R.string.share_disclosure), style = HnType.caption, color = HnColors.InkGrey)
        }
    }
}

@Composable
private fun VideoProgress(video: VideoExportState, onRetry: () -> Unit, onCancel: () -> Unit) {
    when (video) {
        is VideoExportState.Running -> Column {
            LinearProgressIndicator(
                progress = { video.progress },
                color = HnColors.DotRed,
                trackColor = HnColors.DotIdle,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.share_making_video, (video.progress * 100).toInt()),
                    style = HnType.caption,
                    color = HnColors.InkGrey,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = HnDims.minTouch)) {
                    Text(stringResource(R.string.action_cancel), color = HnColors.InkBlack)
                }
            }
        }
        VideoExportState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.share_video_failed),
                style = HnType.caption,
                color = HnColors.DotRed,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = HnDims.minTouch)) {
                Text(stringResource(R.string.action_retry), color = HnColors.InkBlack)
            }
        }
        VideoExportState.Idle -> Unit
    }
}

@Composable
private fun FormatSection(
    icon: ImageVector,
    title: String,
    body: String,
    shareLabel: String,
    onShare: () -> Unit,
    saveLabel: String,
    onSave: (() -> Unit)?,
    enabled: Boolean,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(HnDims.rowShape)
            .background(HnColors.CardTint)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(HnDims.circleButton)
                    .clip(CircleShape)
                    .background(HnColors.CardWhite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = HnColors.InkBlack, modifier = Modifier.size(HnDims.circleIcon))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, style = HnType.bodyPrimary, color = HnColors.InkBlack)
                Text(body, style = HnType.bodySecondary, color = HnColors.InkGrey)
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onShare,
                enabled = enabled,
                shape = HnDims.pill,
                colors = ButtonDefaults.buttonColors(containerColor = HnColors.InkBlack, contentColor = HnColors.CardWhite),
                modifier = Modifier.weight(1f).heightIn(min = HnDims.minTouch),
            ) { Text(shareLabel, style = HnType.bodySecondary) }
            if (onSave != null) {
                OutlinedButton(
                    onClick = onSave,
                    enabled = enabled,
                    shape = HnDims.pill,
                    modifier = Modifier.weight(1f).heightIn(min = HnDims.minTouch),
                ) { Text(saveLabel, style = HnType.bodySecondary, color = HnColors.InkBlack) }
            }
        }
    }
}
