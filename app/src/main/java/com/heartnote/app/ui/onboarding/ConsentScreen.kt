package com.heartnote.app.ui.onboarding

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.heartnote.app.R
import com.heartnote.app.ui.components.HeartnoteIcons
import com.heartnote.app.ui.components.TwoToneHeading
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnDims
import com.heartnote.app.ui.theme.HnType

/** F1 consent flag; the camera/mic permission request happens only after it is set. */
object ConsentStore {
    private const val PREFS = "heartnote"
    private const val KEY = "consent_accepted_v1"

    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun accept(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }
}

/** DESIGN.md § ConsentScreen (derived style). PROCESS_FLOW: Decline stays here. */
@Composable
fun ConsentScreen(onAccepted: () -> Unit) {
    val context = LocalContext.current
    var declined by rememberSaveable { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Granted or not, continue: RecordScreen explains and re-asks if needed.
        onAccepted()
    }
    ConsentContent(
        declined = declined,
        onAccept = {
            ConsentStore.accept(context)
            permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        },
        onDecline = { declined = true },
    )
}

@Composable
internal fun ConsentContent(declined: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(HnColors.LightBg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HnDims.screenPadding),
    ) {
        Spacer(Modifier.height(48.dp))
        TwoToneHeading(stringResource(R.string.consent_heading_grey), stringResource(R.string.consent_heading_black))
        Spacer(Modifier.height(28.dp))
        ConsentRow(HeartnoteIcons.Camera, stringResource(R.string.consent_camera))
        ConsentRow(HeartnoteIcons.Phone, stringResource(R.string.consent_phone))
        ConsentRow(HeartnoteIcons.Trash, stringResource(R.string.consent_delete))
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.consent_not_medical),
            style = HnType.caption,
            color = HnColors.InkGrey,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (declined) {
            Text(
                stringResource(R.string.consent_declined),
                style = HnType.bodySecondary,
                color = HnColors.InkBlack,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        Button(
            onClick = onAccept,
            shape = HnDims.pill,
            colors = ButtonDefaults.buttonColors(containerColor = HnColors.InkBlack, contentColor = HnColors.CardWhite),
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(stringResource(R.string.consent_accept), style = HnType.bodyPrimary)
        }
        TextButton(
            onClick = onDecline,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp)
                .heightIn(min = HnDims.minTouch),
        ) {
            Text(stringResource(R.string.consent_decline), style = HnType.bodyPrimary, color = HnColors.InkGrey)
        }
    }
}

@Composable
private fun ConsentRow(icon: ImageVector, text: String) {
    Row(
        Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .clip(HnDims.rowShape)
            .background(HnColors.CardWhite)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(HnDims.circleButton)
                .clip(CircleShape)
                .background(HnColors.LightBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = HnColors.InkBlack, modifier = Modifier.size(HnDims.circleIcon))
        }
        Text(text, style = HnType.bodySecondary.copy(fontSize = HnType.bodyPrimary.fontSize), color = HnColors.InkBlack)
    }
}

@Preview(name = "Consent", widthDp = 393, heightDp = 852)
@Composable
private fun ConsentPreview() {
    ConsentContent(declined = false, onAccept = {}, onDecline = {})
}
