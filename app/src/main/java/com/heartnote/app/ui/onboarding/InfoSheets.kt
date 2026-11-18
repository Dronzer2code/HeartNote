package com.heartnote.app.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.heartnote.app.BuildConfig
import com.heartnote.app.R
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnDims
import com.heartnote.app.ui.theme.HnType
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.heartnote.app.haptics.HapticStrength
import com.heartnote.app.haptics.HapticsSettings
import com.heartnote.app.haptics.HeartbeatHaptics
import com.heartnote.app.ui.beat.BeatKind

/** Fix 4: Soft / Medium / Strong (default Strong), a test beat, and the system settings. */
@Composable
private fun VibrationSettings() {
    val context = LocalContext.current
    val haptics = remember { HeartbeatHaptics(context) }
    val strength by HapticsSettings.strength.collectAsState()
    Text(stringResource(R.string.vibration_title), style = HnType.bodyPrimary, color = HnColors.InkBlack, modifier = Modifier.padding(top = 16.dp))
    Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(HnDims.pill)
            .background(HnColors.CardTint)
            .selectableGroup(),
    ) {
        listOf(
            HapticStrength.SOFT to R.string.strength_soft,
            HapticStrength.MEDIUM to R.string.strength_medium,
            HapticStrength.STRONG to R.string.strength_strong,
        ).forEach { (value, label) ->
            val selected = strength == value
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = HnDims.minTouch)
                    .clip(HnDims.pill)
                    .background(if (selected) HnColors.InkBlack else HnColors.CardTint)
                    .selectable(selected = selected, role = Role.RadioButton) {
                        HapticsSettings.setStrength(context, value)
                        haptics.beat(BeatKind.LUB)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label), style = HnType.bodySecondary, color = if (selected) HnColors.CardWhite else HnColors.InkBlack)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { haptics.beat(BeatKind.LUB) }, modifier = Modifier.heightIn(min = HnDims.minTouch)) {
            Text(stringResource(R.string.vibration_test), color = HnColors.DotRed)
        }
        TextButton(onClick = { HeartbeatHaptics.openSoundSettings(context) }, modifier = Modifier.heightIn(min = HnDims.minTouch)) {
            Text(stringResource(R.string.vibration_settings), color = HnColors.InkBlack)
        }
    }
}

/**
 * Privacy & about: TRD § CONSENT AND DATA HANDLING in plain words (incl. the
 * SHARE_AND_IMPORT.md additions), what Heartnote is, credits, Replay tour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySheet(onDismiss: () -> Unit, onReplayTour: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HnColors.CardWhite) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
        ) {
            Text(stringResource(R.string.settings_title), style = HnType.cardTitle, color = HnColors.InkBlack)
            VibrationSettings()
            Text(stringResource(R.string.privacy_title), style = HnType.cardTitle, color = HnColors.InkBlack, modifier = Modifier.padding(top = 24.dp))
            listOf(
                R.string.privacy_not_stored_title to R.string.privacy_not_stored,
                R.string.privacy_stored_title to R.string.privacy_stored,
                R.string.privacy_sharing_title to R.string.privacy_sharing,
                R.string.privacy_shared_title to R.string.privacy_shared,
                R.string.privacy_saved_title to R.string.privacy_saved,
                R.string.privacy_received_title to R.string.privacy_received,
                R.string.privacy_delete_title to R.string.privacy_delete,
                R.string.privacy_servers_title to R.string.privacy_servers,
            ).forEach { (heading, body) ->
                Text(stringResource(heading), style = HnType.bodyPrimary, color = HnColors.InkBlack, modifier = Modifier.padding(top = 16.dp))
                Text(stringResource(body), style = HnType.bodySecondary, color = HnColors.InkBlack, modifier = Modifier.padding(top = 4.dp))
            }

            Text(stringResource(R.string.about_title), style = HnType.cardTitle, color = HnColors.InkBlack, modifier = Modifier.padding(top = 28.dp))
            Text(stringResource(R.string.about_body), style = HnType.bodySecondary, color = HnColors.InkBlack, modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.consent_not_medical), style = HnType.bodySecondary, color = HnColors.InkBlack, modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.about_credits_title), style = HnType.bodyPrimary, color = HnColors.InkBlack, modifier = Modifier.padding(top = 16.dp))
            listOf(R.string.about_credit_presage, R.string.about_credit_inter, R.string.about_credit_lucide, R.string.about_credit_design).forEach {
                Text("\u2022  " + stringResource(it), style = HnType.bodySecondary, color = HnColors.InkBlack, modifier = Modifier.padding(top = 6.dp))
            }
            TextButton(onClick = onReplayTour, modifier = Modifier.padding(top = 12.dp).heightIn(min = HnDims.minTouch)) {
                Text(stringResource(R.string.replay_tour), style = HnType.bodyPrimary, color = HnColors.DotRed)
            }
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = HnType.caption,
                color = HnColors.InkGrey,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
