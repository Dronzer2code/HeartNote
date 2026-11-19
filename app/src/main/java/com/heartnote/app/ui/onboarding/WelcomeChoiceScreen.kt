package com.heartnote.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.heartnote.app.R
import com.heartnote.app.ui.components.TwoToneHeading
import com.heartnote.app.ui.components.HeartnoteLogo
import com.heartnote.app.ui.components.entrance
import com.heartnote.app.ui.components.warmGlow
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnDims
import com.heartnote.app.ui.theme.HnType

/** ONBOARDING.md § WelcomeChoice: shown once after Consent while the tour is not done. */
@Composable
fun WelcomeChoiceScreen(onTour: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(HnColors.LightBg)
            .warmGlow(centerX = 0.5f, centerY = 0.3f, radiusFraction = 0.5f, alpha = 0.10f)
            .safeDrawingPadding()
            .padding(horizontal = HnDims.screenPadding),
    ) {
        Spacer(Modifier.weight(0.8f))
        HeartnoteLogo(
            120.dp,
            Modifier
                .align(Alignment.CenterHorizontally)
                .entrance(0),
            glow = true,
        )
        Spacer(Modifier.height(40.dp))
        TwoToneHeading(
            stringResource(R.string.welcome_heading_grey),
            stringResource(R.string.welcome_heading_black),
            Modifier.entrance(1),
        )
        Text(
            stringResource(R.string.welcome_line),
            style = HnType.bodySecondary,
            color = HnColors.InkGrey,
            modifier = Modifier.padding(top = 12.dp).entrance(2),
        )
        Spacer(Modifier.weight(1.2f))
        PrimaryPill(stringResource(R.string.welcome_tour), onTour, Modifier.entrance(3))
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .entrance(4)
                .padding(vertical = 8.dp)
                .heightIn(min = HnDims.minTouch),
        ) {
            Text(stringResource(R.string.action_skip), style = HnType.bodyPrimary, color = HnColors.InkGrey)
        }
    }
}

/** Full-width 56 dp black pill (DESIGN.md primary button). */
@Composable
internal fun PrimaryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = HnDims.pill,
        colors = ButtonDefaults.buttonColors(containerColor = HnColors.InkBlack, contentColor = HnColors.CardWhite),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(text, style = HnType.bodyPrimary)
    }
}

@Preview(name = "WelcomeChoice", widthDp = 393, heightDp = 852)
@Composable
private fun WelcomeChoicePreview() {
    WelcomeChoiceScreen(onTour = {}, onSkip = {})
}
