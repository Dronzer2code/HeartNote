package com.heartnote.app.ui.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartnote.app.R
import com.heartnote.app.ui.beat.BeatBus
import com.heartnote.app.ui.components.CameraPreview
import com.heartnote.app.ui.components.CircleIconButton
import com.heartnote.app.ui.components.DotMatrixNumber
import com.heartnote.app.ui.components.HeartnoteIcons
import com.heartnote.app.ui.components.ParticleSphere
import com.heartnote.app.ui.components.PillAction
import com.heartnote.app.ui.components.ProgressLine
import com.heartnote.app.ui.components.ProgressStyle
import com.heartnote.app.ui.components.SignalWaveform
import com.heartnote.app.ui.components.SlideActionPill
import com.heartnote.app.ui.formatDuration
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnDims
import com.heartnote.app.ui.theme.HnType

private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

/** DESIGN.md § RecordScreen (reference: middle screen). F2, F3, F4, F5. */
@Composable
fun RecordScreen(
    onBack: () -> Unit,
    onNoteSaved: (String) -> Unit,
    viewModel: RecordViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun allGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var permissionsGranted by remember { mutableStateOf(allGranted()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (permissionsGranted) viewModel.startPreview()
    }

    LaunchedEffect(state.savedNoteId) {
        state.savedNoteId?.let { id ->
            viewModel.onSavedNoteShown()
            onNoteSaved(id)
        }
    }

    // Recording is hands-free: without this the screen times out mid-note, the app goes to
    // the background and the note is stopped (Battery saver shortens the timeout further).
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    permissionsGranted = allGranted()
                    if (permissionsGranted) viewModel.startPreview()
                }
                Lifecycle.Event.ON_STOP -> viewModel.onScreenStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    RecordScreenContent(
        state = state,
        permissionsGranted = permissionsGranted,
        onBack = onBack,
        onRequestPermissions = { permissionLauncher.launch(REQUIRED_PERMISSIONS) },
        onRecordingMyself = viewModel::setRecordingMyself,
        onPillAction = viewModel::onPillAction,
        onRetry = viewModel::retryProcessing,
        onDeleteFailed = viewModel::deleteFailedNote,
        sphere = { size ->
            // The SDK shares the phone with this sphere: cap it at 30 fps, and drop the blur
            // glow while measuring, so a busy or warm phone keeps the camera frame rate up.
            val measuring = state.phase == RecordPhase.LOCK_ON || state.phase == RecordPhase.RECORDING ||
                state.phase == RecordPhase.FLUSHING
            ParticleSphere(
                beatEvents = BeatBus.events,
                breath = { viewModel.breathNormalised },
                modifier = Modifier.size(size),
                maxFps = SPHERE_MAX_FPS,
                glow = !measuring,
            )
        },
        camera = { modifier, desaturate ->
            CameraPreview(onSurfaceProviderReady = viewModel::attachPreview, modifier = modifier, desaturate = desaturate)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordScreenContent(
    state: RecordUiState,
    permissionsGranted: Boolean,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRecordingMyself: (Boolean) -> Unit,
    onPillAction: () -> Unit,
    onRetry: () -> Unit,
    onDeleteFailed: () -> Unit,
    sphere: @Composable (Dp) -> Unit,
    camera: @Composable (Modifier, Boolean) -> Unit,
) {
    var showTips by remember { mutableStateOf(false) }
    val phase = state.phase

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HnColors.DarkBg)
            .safeDrawingPadding()
            .padding(horizontal = HnDims.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Top bar
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(HeartnoteIcons.Back, stringResource(R.string.cd_back), onBack, dark = true)
            Text(
                stringResource(R.string.record_title),
                style = HnType.screenTitle,
                color = HnColors.OnDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            CircleIconButton(HeartnoteIcons.Lightbulb, stringResource(R.string.cd_lighting_tips), { showTips = true }, dark = true)
        }
        // 2. Subtitle
        Text(
            stringResource(R.string.record_subtitle),
            style = HnType.caption,
            color = HnColors.InkGrey,
            modifier = Modifier.padding(top = 12.dp),
        )

        Spacer(Modifier.weight(1f))

        // 3. Sphere with camera + live BPM in its core
        Box(Modifier.size(SPHERE_SIZE), contentAlignment = Alignment.Center) {
            sphere(SPHERE_SIZE)
            if (permissionsGranted) {
                val showCamera = phase == RecordPhase.CONFIRM || phase == RecordPhase.PREVIEW || phase == RecordPhase.LOCK_ON
                val cameraAlpha by animateFloatAsState(
                    targetValue = if (showCamera) CAMERA_ALPHA else CAMERA_ALPHA_RECORDING,
                    animationSpec = tween(CAMERA_FADE_MS),
                    label = "cameraAlpha",
                )
                camera(
                    Modifier
                        .size(SPHERE_SIZE * CAMERA_FRACTION)
                        .clip(CircleShape)
                        .alpha(cameraAlpha),
                    true,
                )
            }
            BpmReadout(state.pulseBpm)
        }

        Spacer(Modifier.weight(1f))

        if (!permissionsGranted) {
            Text(
                stringResource(R.string.record_permission_needed),
                style = HnType.recordStatusSecondary,
                color = HnColors.InkGrey,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onRequestPermissions, modifier = Modifier.heightIn(min = HnDims.minTouch)) {
                Text(stringResource(R.string.record_permission_button), style = HnType.bodyPrimary, color = HnColors.OnDark)
            }
            Spacer(Modifier.height(24.dp))
        } else {
            // 4. Status (two lines)
            StatusLines(state)

            // Confirm: F2 checkbox
            if (phase == RecordPhase.CONFIRM || phase == RecordPhase.PREVIEW) {
                val checked = phase == RecordPhase.PREVIEW
                Row(
                    Modifier
                        .padding(top = 4.dp)
                        .heightIn(min = HnDims.minTouch)
                        .toggleable(value = checked, role = Role.Checkbox, onValueChange = onRecordingMyself),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = HnColors.SignalRed,
                            uncheckedColor = HnColors.InkGrey,
                            checkmarkColor = HnColors.CardWhite,
                        ),
                    )
                    Text(
                        stringResource(R.string.record_confirm_checkbox),
                        style = HnType.bodySecondary,
                        color = HnColors.OnDark,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            // 7. Breathing waveform (above the progress line)
            val breathingDescription = stringResource(R.string.record_breathing_cd)
            SignalWaveform(
                values = state.breathingWave,
                color = HnColors.InkGrey,
                strokeWidth = 1.5.dp,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(PROGRESS_WIDTH)
                    .height(24.dp)
                    .semantics { contentDescription = breathingDescription },
            )

            // 5. Progress line
            ProgressBlock(state, Modifier.padding(top = 12.dp))

            // 6. Pill, or Retry/Delete when processing failed
            Box(Modifier.padding(top = 20.dp, bottom = 16.dp).height(64.dp), contentAlignment = Alignment.Center) {
                if (phase == RecordPhase.FAILED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = onDeleteFailed, modifier = Modifier.heightIn(min = HnDims.minTouch)) {
                            Text(stringResource(R.string.action_delete), style = HnType.bodyPrimary, color = HnColors.InkGrey)
                        }
                        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = HnDims.minTouch)) {
                            Text(stringResource(R.string.action_retry), style = HnType.bodyPrimary, color = HnColors.OnDark)
                        }
                    }
                } else {
                    val stop = phase == RecordPhase.LOCK_ON || phase == RecordPhase.RECORDING ||
                        phase == RecordPhase.FLUSHING || phase == RecordPhase.PROCESSING
                    SlideActionPill(
                        action = if (stop) PillAction.STOP else PillAction.START,
                        label = stringResource(if (stop) R.string.pill_stop else R.string.pill_start),
                        onAction = onPillAction,
                        enabled = when (phase) {
                            RecordPhase.PREVIEW -> state.facePresent
                            RecordPhase.LOCK_ON, RecordPhase.RECORDING -> true
                            else -> false
                        },
                    )
                }
            }
        }
    }

    if (showTips) {
        ModalBottomSheet(onDismissRequest = { showTips = false }, containerColor = HnColors.CardWhite) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text(stringResource(R.string.tips_title), style = HnType.cardTitle, color = HnColors.InkBlack)
                listOf(R.string.tips_light, R.string.tips_distance, R.string.tips_still, R.string.tips_speak).forEach {
                    Text(
                        "•  " + stringResource(it),
                        style = HnType.bodySecondary,
                        color = HnColors.InkBlack,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BpmReadout(bpm: Int?) {
    val description = bpm?.let { stringResource(R.string.record_bpm_cd, it.toString()) }
        ?: stringResource(R.string.record_bpm_cd_none)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DotMatrixNumber(
            value = bpm?.coerceIn(0, 999)?.toString() ?: "--",
            dotColor = HnColors.OnDark,
            digitHeight = 72.dp,
            contentDescription = description,
        )
        Text(
            stringResource(R.string.record_bpm_unit),
            style = HnType.progressLabel,
            color = HnColors.OnDark,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            stringResource(R.string.record_disclaimer),
            style = HnType.disclaimer,
            color = HnColors.InkGrey,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun StatusLines(state: RecordUiState) {
    val (primary, secondary) = when (state.phase) {
        RecordPhase.CONFIRM -> R.string.record_confirm_primary to stringResource(R.string.record_confirm_secondary)
        RecordPhase.PREVIEW -> R.string.record_preview_primary to
            (state.info?.let { stringResource(it.text) } ?: stringResource(R.string.record_preview_secondary))
        RecordPhase.LOCK_ON -> R.string.record_preview_primary to stringResource(R.string.record_lockon_secondary)
        RecordPhase.RECORDING -> R.string.record_recording_primary to stringResource(
            R.string.record_recording_secondary,
            formatDuration(state.recordedMs),
            formatDuration(RecordViewModel.MAX_NOTE_DURATION_MS),
        )
        RecordPhase.FLUSHING -> R.string.record_flushing_primary to stringResource(R.string.record_flushing_secondary)
        RecordPhase.PROCESSING, RecordPhase.SAVED ->
            R.string.record_processing_primary to stringResource(R.string.record_processing_secondary)
        RecordPhase.FAILED -> R.string.record_failed_primary to stringResource(R.string.record_failed_secondary)
    }
    // SDK hint (e.g. face not found) replaces the secondary line while measuring.
    val hint = state.validationHint?.takeIf {
        state.phase == RecordPhase.PREVIEW || state.phase == RecordPhase.LOCK_ON || state.phase == RecordPhase.RECORDING
    }
    val error = state.sdkError
    val faceWarning = if (state.phase == RecordPhase.RECORDING && !state.facePresent) {
        stringResource(R.string.record_recording_face_warning)
    } else {
        null
    }
    // Battery saver / warm phone: shown while waiting to start, when it explains a slow start.
    val slowHint = state.slowCameraHint?.takeIf {
        state.phase == RecordPhase.CONFIRM || state.phase == RecordPhase.PREVIEW || state.phase == RecordPhase.LOCK_ON
    }?.let { stringResource(it) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(primary), style = HnType.recordStatus, color = HnColors.OnDark, textAlign = TextAlign.Center)
        Text(
            error ?: faceWarning ?: hint ?: slowHint ?: secondary,
            style = HnType.recordStatusSecondary,
            color = if (error != null) HnColors.SignalRed else HnColors.InkGrey,
            textAlign = TextAlign.Center,
            minLines = 1,
            maxLines = 3,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun ProgressBlock(state: RecordUiState, modifier: Modifier) {
    when (state.phase) {
        RecordPhase.LOCK_ON -> ProgressLine(
            state.lockOnFraction,
            stringResource(R.string.record_percent, (state.lockOnFraction * 100).toInt()),
            modifier,
        )
        RecordPhase.RECORDING -> ProgressLine(
            state.recordedMs.toFloat() / RecordViewModel.MAX_NOTE_DURATION_MS,
            formatDuration(state.recordedMs),
            modifier,
        )
        RecordPhase.FLUSHING -> ProgressLine(1f, " ", modifier, ProgressStyle.PULSING)
        RecordPhase.PROCESSING -> ProgressLine(0f, " ", modifier, ProgressStyle.INDETERMINATE)
        // Hidden in Confirm/Preview; keep the space so the layout does not jump.
        else -> Spacer(modifier.height(PROGRESS_BLOCK_HEIGHT))
    }
}

private val SPHERE_SIZE = 320.dp
private const val SPHERE_MAX_FPS = 30
private const val CAMERA_FRACTION = 0.62f
private const val CAMERA_ALPHA = 0.45f
/** DESIGN.md: 0% while recording; switch to 0.12f if the SDK stops receiving frames at 0. */
private const val CAMERA_ALPHA_RECORDING = 0f
private const val CAMERA_FADE_MS = 600
private val PROGRESS_WIDTH = 175.dp
private val PROGRESS_BLOCK_HEIGHT = 34.dp

@Preview(name = "Record — recording", widthDp = 393, heightDp = 852, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun RecordScreenRecordingPreview() {
    RecordPreviewFrame(RecordUiState(phase = RecordPhase.RECORDING, pulseBpm = 85, recordedMs = 23_000, breathingWave = previewWave()))
}

@Preview(name = "Record — confirm", widthDp = 393, heightDp = 852, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun RecordScreenConfirmPreview() {
    RecordPreviewFrame(RecordUiState(phase = RecordPhase.CONFIRM))
}

@Preview(name = "Record — lock-on", widthDp = 393, heightDp = 852, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun RecordScreenLockOnPreview() {
    RecordPreviewFrame(RecordUiState(phase = RecordPhase.LOCK_ON, lockOnFraction = 0.12f, facePresent = true))
}

@Composable
private fun RecordPreviewFrame(state: RecordUiState) {
    RecordScreenContent(
        state = state,
        permissionsGranted = true,
        onBack = {}, onRequestPermissions = {}, onRecordingMyself = {}, onPillAction = {}, onRetry = {}, onDeleteFailed = {},
        sphere = { size -> ParticleSphere(beatEvents = null, breath = { 0f }, modifier = Modifier.size(size)) },
        camera = { modifier, _ -> Box(modifier.background(HnColors.DarkSurface)) },
    )
}

private fun previewWave() = List(100) { kotlin.math.sin(it / 8f) }
