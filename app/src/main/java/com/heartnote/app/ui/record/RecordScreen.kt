package com.heartnote.app.ui.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartnote.app.ui.components.CameraPreview
import com.heartnote.app.ui.components.SignalWaveform

private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

/** F3 (lock-on), F4 (record), F5 (live signals). F2 confirm gate arrives day 5. */
@Composable
fun RecordScreen(
    onNoteSaved: (String) -> Unit,
    viewModel: RecordViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(uiState.savedNoteId) {
        uiState.savedNoteId?.let { id ->
            viewModel.onSavedNoteShown()
            onNoteSaved(id)
        }
    }

    fun allGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var permissionsGranted by remember { mutableStateOf(allGranted()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (permissionsGranted) viewModel.startPreview()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (permissionsGranted) viewModel.startPreview()
                Lifecycle.Event.ON_STOP -> viewModel.onScreenStop()
                else -> Unit
            }
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

        if (uiState.phase == RecordPhase.SAVED) {
            SavedSummary(uiState, onRecordAnother = viewModel::tapRecordAnother)
            return@Column
        }

        if (permissionsGranted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.extraLarge),
            ) {
                CameraPreview(
                    onSurfaceProviderReady = viewModel::attachPreview,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Text(
                "Heartnote needs the camera to read your pulse and breathing, " +
                    "and the microphone to record your voice.",
                textAlign = TextAlign.Center,
            )
        }

        if (uiState.phase == RecordPhase.RECORDING || uiState.phase == RecordPhase.FLUSHING) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Pulse", style = MaterialTheme.typography.labelMedium)
                        Text(
                            uiState.pulseBpm?.toString() ?: "—",
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text("BPM", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Card(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Breathing", style = MaterialTheme.typography.labelMedium)
                        SignalWaveform(
                            values = uiState.breathingWave,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        )
                        // brRate is sparse/noisy: number only as secondary text when fresh
                        Text(
                            uiState.breathingRpm?.let { "~$it breaths/min" } ?: " ",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Text(
                "Live signal — not a medical reading",
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Text(
            statusLine(uiState),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        uiState.infoMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        uiState.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }

        when (uiState.phase) {
            RecordPhase.PREVIEW -> Button(
                onClick = {
                    if (permissionsGranted) viewModel.tapStart()
                    else permissionLauncher.launch(REQUIRED_PERMISSIONS)
                },
                enabled = !permissionsGranted || uiState.facePresent,
            ) {
                Text(
                    when {
                        !permissionsGranted -> "Allow camera & microphone"
                        uiState.facePresent -> "Start recording"
                        else -> "Looking for your face…"
                    }
                )
            }
            RecordPhase.LOCK_ON -> OutlinedButton(onClick = viewModel::tapCancelLockOn) {
                Text("Cancel")
            }
            RecordPhase.RECORDING -> Button(onClick = viewModel::tapStop) {
                Text("Stop  ·  ${formatSeconds(uiState.recordedSeconds)} / 1:00")
            }
            RecordPhase.FLUSHING -> Button(onClick = {}, enabled = false) {
                Text("Finishing your heartbeat…")
            }
            RecordPhase.PROCESSING -> Button(onClick = {}, enabled = false) {
                Text("Making your Heartnote…")
            }
            RecordPhase.FAILED -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = viewModel::deleteFailedNote) { Text("Delete") }
                Button(onClick = viewModel::retryProcessing) { Text("Retry") }
            }
            RecordPhase.SAVED -> Unit
        }
    }
}

@Composable
private fun SavedSummary(uiState: RecordUiState, onRecordAnother: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Saved", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Your ${formatSeconds(((uiState.savedDurationMs ?: 0L) / 1000).toInt())} note " +
                    "was saved with its raw signal track.",
                textAlign = TextAlign.Center,
            )
            Text(
                "notes/${uiState.savedNoteId}/\nvoice.wav · signals.json",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
    Button(onClick = onRecordAnother) { Text("Record another") }
}

private fun statusLine(state: RecordUiState): String {
    val base = when (state.phase) {
        RecordPhase.PREVIEW -> when {
            state.facePresent -> "Ready when you are"
            else -> "Center your face in the frame"
        }
        RecordPhase.LOCK_ON -> "Hold still — finding your pulse…"
        RecordPhase.RECORDING -> "Recording — speak naturally, keep your face steady"
        RecordPhase.FLUSHING -> "Finishing your heartbeat…"
        RecordPhase.PROCESSING -> "Making your Heartnote…"
        RecordPhase.FAILED -> "Could not finish this note"
        RecordPhase.SAVED -> ""
    }
    return state.validationHint?.let { "$base\n$it" } ?: base
}

private fun formatSeconds(totalSeconds: Int): String =
    "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
