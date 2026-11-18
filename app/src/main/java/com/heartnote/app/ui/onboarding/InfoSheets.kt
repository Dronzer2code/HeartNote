package com.heartnote.app.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.heartnote.app.BuildConfig
import com.heartnote.app.R
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnType

/** Privacy sheet: TRD § CONSENT AND DATA HANDLING in plain words. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HnColors.CardWhite) {
        SheetBody(
            title = stringResource(R.string.privacy_title),
            sections = listOf(
                R.string.privacy_not_stored_title to R.string.privacy_not_stored,
                R.string.privacy_stored_title to R.string.privacy_stored,
                R.string.privacy_sharing_title to R.string.privacy_sharing,
                R.string.privacy_delete_title to R.string.privacy_delete,
                R.string.privacy_servers_title to R.string.privacy_servers,
            ),
            footer = stringResource(R.string.consent_not_medical),
        )
    }
}

/** About sheet with credits. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit, onReplayTour: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HnColors.CardWhite) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
        ) {
            Text(stringResource(R.string.about_title), style = HnType.cardTitle, color = HnColors.InkBlack)
            Text(stringResource(R.string.about_body), style = HnType.bodySecondary, color = HnColors.InkBlack, modifier = Modifier.padding(top = 12.dp))
            Text(stringResource(R.string.consent_not_medical), style = HnType.bodySecondary, color = HnColors.InkBlack, modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.about_credits_title), style = HnType.bodyPrimary, color = HnColors.InkBlack, modifier = Modifier.padding(top = 20.dp))
            listOf(R.string.about_credit_presage, R.string.about_credit_inter, R.string.about_credit_lucide, R.string.about_credit_design).forEach {
                Text("•  " + stringResource(it), style = HnType.bodySecondary, color = HnColors.InkBlack, modifier = Modifier.padding(top = 6.dp))
            }
            TextButton(onClick = onReplayTour, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.replay_tour), style = HnType.bodyPrimary, color = HnColors.DotRed)
            }
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = HnType.caption,
                color = HnColors.InkGrey,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

@Composable
private fun SheetBody(title: String, sections: List<Pair<Int, Int>>, footer: String) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(title, style = HnType.cardTitle, color = HnColors.InkBlack)
        sections.forEach { (heading, body) ->
            Text(stringResource(heading), style = HnType.bodyPrimary, color = HnColors.InkBlack, modifier = Modifier.padding(top = 16.dp))
            Text(stringResource(body), style = HnType.bodySecondary, color = HnColors.InkBlack, modifier = Modifier.padding(top = 4.dp))
        }
        Text(footer, style = HnType.caption, color = HnColors.InkGrey, modifier = Modifier.padding(top = 20.dp))
    }
}
