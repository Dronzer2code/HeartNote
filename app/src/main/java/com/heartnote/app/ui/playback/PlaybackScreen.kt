package com.heartnote.app.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartnote.app.R
import com.heartnote.app.data.NoteMeta
import com.heartnote.app.data.SmileSegment
import com.heartnote.app.share.BeatRecovery
import com.heartnote.app.ui.share.NoteShareSheet
import com.heartnote.app.ui.share.ShareViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.heartnote.app.signal.BeatSource
import com.heartnote.app.haptics.HeartbeatHaptics
import androidx.compose.foundation.layout.heightIn
import com.heartnote.app.signal.TimedValue
import com.heartnote.app.ui.beat.BeatBus
import com.heartnote.app.ui.beat.BeatKind
import com.heartnote.app.ui.components.ArcNavBar
import com.heartnote.app.ui.components.CircleIconButton
import com.heartnote.app.ui.components.DotBarChart
import com.heartnote.app.ui.components.DotMatrixNumber
import com.heartnote.app.ui.components.HeartnoteIcons
import com.heartnote.app.ui.components.NavAction
import com.heartnote.app.ui.components.TwoToneHeading
import com.heartnote.app.ui.components.dotBarChartHeight
import com.heartnote.app.ui.components.previewDotValues
import com.heartnote.app.ui.formatDuration
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnDims
import com.heartnote.app.ui.theme.HnType

/** R6 label; received notes say "their" pulse, recovered beats say so. */
fun beatLabel(meta: NoteMeta): Int = when {
    meta.beatSource == BeatRecovery.SOURCE -> R.string.beat_label_recovered
    BeatSource.fromKey(meta.beatSource) == BeatSource.TRACE ->
        if (meta.isReceived) R.string.beat_label_trace_their else R.string.beat_label_trace
    else -> if (meta.isReceived) R.string.beat_label_rate_their else R.string.beat_label_rate
}

/** Beat-flash target: the dot column holding the latest played beat. */
data class BeatFlash(val row: Int, val column: Int, val key: Int)

/** DESIGN.md § PlaybackScreen (reference: right screen). F7, F8, F9. No pulse numbers (C1). */
@Composable
fun PlaybackScreen(
    onBack: () -> Unit,
    onHold: (String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: PlaybackViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_chooser_title)
    val scope = rememberCoroutineScope()
    var flash by remember { mutableStateOf<BeatFlash?>(null) }
    var flashCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        BeatBus.events.collect { e ->
            val t = e.noteTimeMs ?: return@collect
            if (e.kind != BeatKind.LUB) return@collect
            val (row, col) = DotChartData.rowColumnOf(t)
            flash = BeatFlash(row, col, ++flashCount)
        }
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    val share: ShareViewModel = viewModel()
    NoteShareSheet(share)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }

    val rows = remember(state.signals) {
        state.signals?.let { DotChartData.playbackRows(it) }
    }

    if (state.loadFailed) {
        Box(Modifier.fillMaxSize().background(HnColors.LightBg).safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.playback_load_failed), style = HnType.bodyPrimary, color = HnColors.InkBlack)
        }
        return
    }
    val meta = state.meta ?: return
    PlaybackContent(
        meta = meta,
        rows = rows ?: emptyList(),
        smiles = state.signals?.smiles.orEmpty(),
        player = state.player,
        flash = flash,
        vibrationAvailable = state.vibrationAvailable,
        bluetoothOutput = state.bluetoothOutput,
        onBack = onBack,
        onShare = { share.open(meta) },
        onTogglePlay = viewModel::togglePlay,
        onSeek = viewModel::seekTo,
        onHold = { viewModel.pause(); onHold(meta.id) },
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
    )
}

@Composable
internal fun PlaybackContent(
    meta: NoteMeta,
    rows: List<FloatArray>,
    smiles: List<SmileSegment>,
    player: PlayerState,
    flash: BeatFlash?,
    vibrationAvailable: Boolean,
    bluetoothOutput: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onHold: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val started = player.isPlaying || player.positionMs > 0
    val sourceLabel = stringResource(beatLabel(meta))

    Box(Modifier.fillMaxSize().background(HnColors.LightBg)) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HnDims.screenPadding)
                .padding(bottom = NAV_SPACE),
        ) {
            // 1. Top bar
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(HeartnoteIcons.Back, stringResource(R.string.cd_back), onBack)
                Text(
                    stringResource(R.string.playback_title),
                    style = HnType.screenTitle,
                    color = HnColors.InkBlack,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                CircleIconButton(HeartnoteIcons.Download, stringResource(R.string.cd_export), onShare)
            }

            // 2. Heading (tap = rename) + smile heart
            val renameLabel = stringResource(R.string.cd_rename_heading)
            Row(Modifier.padding(top = 28.dp), verticalAlignment = Alignment.Bottom) {
                TwoToneHeading(
                    stringResource(R.string.playback_heading_grey),
                    meta.title,
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Button, onClickLabel = renameLabel) { renaming = true },
                )
                if (smiles.isNotEmpty()) {
                    CircleIconButton(
                        HeartnoteIcons.Heart,
                        stringResource(R.string.cd_first_smile),
                        { onSeek(smiles.first().startMs) },
                        size = HnDims.minTouch,
                        containerColor = HnColors.LightBg,
                        iconColor = HnColors.DotRed,
                    )
                }
            }

            // 3–5. Card with three 8-second dot rows
            Column(
                Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
                    .background(HnColors.CardWhite, HnDims.cardShape)
                    .padding(HnDims.cardPadding),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.playback_card_title), style = HnType.cardTitle, color = HnColors.InkBlack)
                        Text(
                            stringResource(R.string.playback_card_subtitle, sourceLabel, formatDuration(meta.durationMs)),
                            style = HnType.cardSubtitle,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        DotMatrixNumber(meta.beatCount.toString(), HnColors.InkBlack, 26.dp)
                        Text(stringResource(R.string.playback_beats), style = HnType.caption, color = HnColors.InkGrey)
                    }
                }
                rows.forEachIndexed { r, values ->
                    DotRow(
                        row = r,
                        values = values,
                        smiles = smiles,
                        progress = if (started) DotChartData.rowProgress(r, player.positionMs) else null,
                        flash = flash?.takeIf { it.row == r },
                        onSeek = onSeek,
                    )
                }
            }

            // 6. Hints
            if (bluetoothOutput) Hint(stringResource(R.string.playback_bluetooth_hint))
            if (!vibrationAvailable) {
                val context = LocalContext.current
                Hint(stringResource(R.string.playback_vibration_off))
                TextButton(
                    onClick = { HeartbeatHaptics.openSoundSettings(context) },
                    modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = HnDims.minTouch),
                ) { Text(stringResource(R.string.open_settings), color = HnColors.DotRed) }
            }
        }

        // 7. Nav
        ArcNavBar(
            outerLeft = NavAction(HeartnoteIcons.Trash, stringResource(R.string.cd_delete), { confirmDelete = true }),
            innerLeft = NavAction(HeartnoteIcons.Share, stringResource(R.string.cd_share), onShare),
            centre = NavAction(
                if (player.isPlaying) HeartnoteIcons.Pause else HeartnoteIcons.Play,
                stringResource(if (player.isPlaying) R.string.playback_pause else R.string.playback_play),
                onTogglePlay,
            ),
            innerRight = NavAction(HeartnoteIcons.HandHeart, stringResource(R.string.cd_hold), onHold),
            outerRight = NavAction(HeartnoteIcons.Pencil, stringResource(R.string.cd_rename), { renaming = true }),
            label = stringResource(
                R.string.playback_position,
                formatDuration(player.positionMs),
                formatDuration(meta.durationMs),
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp),
        )
        com.heartnote.app.ui.components.NavBarInsetFill(Modifier.align(Alignment.BottomCenter))
    }

    if (renaming) RenameDialog(meta.title, onDismiss = { renaming = false }, onSave = { onRename(it); renaming = false })
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete), color = HnColors.DotRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            containerColor = HnColors.CardWhite,
        )
    }
}

@Composable
private fun DotRow(
    row: Int,
    values: FloatArray,
    smiles: List<SmileSegment>,
    progress: Float?,
    flash: BeatFlash?,
    onSeek: (Long) -> Unit,
) {
    val rowStart = row * DotChartData.ROW_MS
    val fromS = (rowStart / 1000).toInt()
    val toS = ((rowStart + DotChartData.ROW_MS) / 1000).toInt()
    val seekLabel = stringResource(R.string.cd_row_seek, fromS, toS)
    Column(Modifier.padding(top = if (row == 0) 14.dp else 6.dp)) {
        // Smile markers: 6 dp DotRed hearts above the row at each smile start.
        BoxWithConstraints(Modifier.fillMaxWidth().height(MARKER_ROW)) {
            val w = maxWidth
            smiles.filter { it.startMs in rowStart until rowStart + DotChartData.ROW_MS }.forEach { s ->
                val x = w * ((s.startMs - rowStart).toFloat() / DotChartData.ROW_MS)
                Icon(
                    HeartnoteIcons.HeartFilled,
                    contentDescription = null,
                    tint = HnColors.DotRed,
                    modifier = Modifier
                        .offset(x = x - MARKER / 2)
                        .size(MARKER),
                )
            }
        }
        DotBarChart(
            values = values,
            rows = 9,
            progress = progress,
            highlightIndex = flash?.column,
            flashKey = flash?.key,
            modifier = Modifier
                .fillMaxWidth()
                .height(dotBarChartHeight(9))
                .semantics {
                    contentDescription = seekLabel
                    onClick { onSeek(rowStart); true }
                }
                .pointerInput(row) {
                    detectTapGestures { offset ->
                        onSeek(rowStart + (offset.x / size.width * DotChartData.ROW_MS).toLong())
                    }
                },
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(stringResource(R.string.playback_axis_seconds, fromS), style = HnType.axis, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.playback_axis_seconds, toS), style = HnType.axis)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = HnType.caption,
        color = HnColors.InkGrey,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_TITLE) },
                label = { Text(stringResource(R.string.rename_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = HnColors.CardWhite,
    )
}

private val NAV_SPACE = 120.dp
private val MARKER = 6.dp
private val MARKER_ROW = 8.dp
private const val MAX_TITLE = 40

@Preview(name = "Playback", widthDp = 393, heightDp = 852)
@Composable
private fun PlaybackPreview() {
    PlaybackContent(
        meta = NoteMeta("a", "Mum", 0, 42_000, beatCount = 59, beatSource = "trace", smileCount = 2),
        rows = List(3) { previewDotValues(40, it + 1) },
        smiles = listOf(SmileSegment(3_200, 4_400), SmileSegment(17_600, 19_000)),
        player = PlayerState(isPlaying = true, positionMs = 12_000, durationMs = 42_000),
        flash = BeatFlash(1, 18, 1),
        vibrationAvailable = true,
        bluetoothOutput = false,
        onBack = {}, onShare = {}, onTogglePlay = {}, onSeek = {}, onHold = {}, onRename = {}, onDelete = {},
    )
}
