package com.heartnote.app.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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

/** F9 revised (SHARE_AND_IMPORT.md § SHARE SHEET): Video (default) or Audio only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    video: VideoExportState,
    onVideo: () -> Unit,
    onAudio: () -> Unit,
    onCancelVideo: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (video !is VideoExportState.Running) onDismiss() },
        containerColor = HnColors.CardWhite,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = HnDims.cardRadius, topEnd = HnDims.cardRadius),
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.share_sheet_title), style = HnType.cardTitle, color = HnColors.InkBlack)
            ShareOption(
                icon = HeartnoteIcons.Play,
                title = stringResource(R.string.share_video),
                body = stringResource(R.string.share_video_desc),
                enabled = video !is VideoExportState.Running,
                onClick = onVideo,
            )
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
                        TextButton(onClick = onCancelVideo, modifier = Modifier.heightIn(min = HnDims.minTouch)) {
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
                    TextButton(onClick = onVideo, modifier = Modifier.heightIn(min = HnDims.minTouch)) {
                        Text(stringResource(R.string.action_retry), color = HnColors.InkBlack)
                    }
                }
                VideoExportState.Idle -> Unit
            }
            ShareOption(
                icon = HeartnoteIcons.Mic,
                title = stringResource(R.string.share_audio),
                body = stringResource(R.string.share_audio_desc),
                enabled = true,
                onClick = onAudio,
            )
            Text(stringResource(R.string.share_tip), style = HnType.caption, color = HnColors.InkGrey, modifier = Modifier.padding(top = 4.dp))
            Text(stringResource(R.string.share_disclosure), style = HnType.caption, color = HnColors.InkGrey)
        }
    }
}

@Composable
private fun ShareOption(icon: ImageVector, title: String, body: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(HnDims.rowShape)
            .background(HnColors.CardTint)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            Text(title, style = HnType.bodyPrimary, color = if (enabled) HnColors.InkBlack else HnColors.InkGrey)
            Text(body, style = HnType.bodySecondary, color = HnColors.InkGrey)
        }
    }
}
