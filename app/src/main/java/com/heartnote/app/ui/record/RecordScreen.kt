package com.heartnote.app.ui.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartnote.app.ui.components.CameraPreview
import com.presagetech.smartspectra.ProcessingStatus

/**
 * Day 1 spike screen (MVP.md day plan): camera preview + live pulse and
 * breathing rate. Grows into F2–F5 on day 2.
 */
@Composable
fun RecordScreen(viewModel: RecordViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        if (granted) viewModel.toggleCapture()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.stopCapture()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Heartnote", style = MaterialTheme.typography.headlineMedium)

        if (cameraGranted) {
            CameraPreview(
                onSurfaceProviderReady = viewModel::attachPreview,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.extraLarge),
            )
        } else {
            Text(
                "Camera access is needed to read your pulse and breathing from the front camera.",
                textAlign = TextAlign.Center,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SignalCard(
                label = "Pulse",
                value = uiState.pulseBpm?.toString() ?: "—",
                unit = "BPM",
                modifier = Modifier.weight(1f),
            )
            SignalCard(
                label = "Breathing",
                value = uiState.breathingRpm?.toString() ?: "—",
                unit = "breaths/min",
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Live signal — not a medical reading",
            style = MaterialTheme.typography.labelSmall,
        )

        Text(
            statusLine(uiState),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        uiState.errorMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        Button(
            onClick = {
                if (cameraGranted) {
                    viewModel.toggleCapture()
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            enabled = uiState.status != ProcessingStatus.STARTING &&
                uiState.status != ProcessingStatus.STOPPING,
        ) {
            Text(
                when (uiState.status) {
                    ProcessingStatus.RUNNING -> "Stop"
                    ProcessingStatus.STARTING -> "Starting…"
                    ProcessingStatus.STOPPING -> "Stopping…"
                    else -> "Start"
                }
            )
        }
    }
}

private fun statusLine(state: RecordUiState): String {
    val base = when (state.status) {
        ProcessingStatus.IDLE -> "Ready"
        ProcessingStatus.STARTING -> "Starting camera…"
        ProcessingStatus.RUNNING -> when {
            !state.hasFace -> "Looking for your face…"
            !state.hasCardio -> "Hold still — finding your pulse…"
            else -> "Measuring"
        }
        ProcessingStatus.STOPPING -> "Stopping…"
        ProcessingStatus.ERROR -> "Something went wrong"
    }
    return state.validationHint?.let { "$base\n$it" } ?: base
}

@Composable
private fun SignalCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.displaySmall)
            Text(unit, style = MaterialTheme.typography.labelSmall)
        }
    }
}
