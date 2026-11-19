package com.heartnote.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartnote.app.R
import com.heartnote.app.data.NoteMeta
import com.heartnote.app.ui.share.NoteShareSheet
import com.heartnote.app.ui.share.ShareViewModel
import com.heartnote.app.ui.components.ArcNavBar
import com.heartnote.app.ui.components.CircleIconButton
import com.heartnote.app.ui.components.DotBarChart
import com.heartnote.app.ui.components.DotMatrixNumber
import com.heartnote.app.ui.components.HeartnoteIcons
import com.heartnote.app.ui.components.NavAction
import com.heartnote.app.ui.components.NavBarInsetFill
import com.heartnote.app.ui.components.TwoToneHeading
import com.heartnote.app.ui.components.previewDotValues
import com.heartnote.app.ui.formatDuration
import com.heartnote.app.ui.formatNoteDate
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnDims
import com.heartnote.app.ui.theme.HnType
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.graphicsLayer
import com.heartnote.app.ui.components.HeartnoteLogo
import com.heartnote.app.ui.components.entrance
import com.heartnote.app.ui.components.rememberCountUp
import com.heartnote.app.ui.components.rememberHeartbeatScale
import com.heartnote.app.ui.components.rememberRise
import com.heartnote.app.ui.components.warmGlow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import com.heartnote.app.share.ImportRequests

/** DESIGN.md § LibraryScreen (reference: left screen). F10. */
@Composable
fun LibraryScreen(
    onRecord: () -> Unit,
    onOpenNote: (String) -> Unit,
    onHold: (String) -> Unit,
    onPrivacy: () -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val shareTitle = stringResource(R.string.share_chooser_title)
    val share: ShareViewModel = viewModel()
    NoteShareSheet(share)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }

    // Share target / Open with: picked up here, after consent (never auto-imported).
    LaunchedEffect(Unit) {
        ImportRequests.pending.collect { if (it != null) ImportRequests.take()?.let(viewModel::import) }
    }
    LaunchedEffect(state.openNoteId) {
        state.openNoteId?.let { id ->
            viewModel.onNoteOpened()
            onOpenNote(id)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LibraryContent(
        state = state,
        onRecord = onRecord,
        onOpenNote = onOpenNote,
        onHold = onHold,
        onShare = share::open,
        onPrivacy = onPrivacy,
        onImport = { picker.launch(IMPORT_TYPES) },
        onDismissMessage = viewModel::dismissMessage,
    )
}

/** SHARE_AND_IMPORT.md entry point 1: picker types (no storage permission needed). */
private val IMPORT_TYPES = arrayOf("video/mp4", "audio/mp4", "audio/*", "application/octet-stream")

@Composable
internal fun LibraryContent(
    state: LibraryUiState,
    onRecord: () -> Unit,
    onOpenNote: (String) -> Unit,
    onHold: (String) -> Unit,
    onShare: (NoteMeta) -> Unit,
    onPrivacy: () -> Unit,
    onImport: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val latest = state.latest
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Box(
        Modifier
            .fillMaxSize()
            .background(HnColors.LightBg)
            // Warm red light behind the header and the Record button (existing DotRed, alpha only).
            .warmGlow(centerX = 0.95f, centerY = 0.02f, radiusFraction = 0.5f, alpha = 0.10f)
            .warmGlow(centerX = 0.5f, centerY = 1.02f, radiusFraction = 0.35f, alpha = 0.08f),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = HnDims.screenPadding,
                end = HnDims.screenPadding,
                top = insets.calculateTopPadding() + 12.dp,
                bottom = insets.calculateBottomPadding() + NAV_SPACE,
            ),
        ) {
            item { TopRow(onPrivacy, Modifier.entrance(0)) }
            item {
                Row(Modifier.padding(top = 28.dp, bottom = 20.dp).entrance(1), verticalAlignment = Alignment.Bottom) {
                    TwoToneHeading(
                        stringResource(R.string.library_heading_grey),
                        stringResource(R.string.library_heading_black),
                    )
                    BeatingHeart(Modifier.padding(start = 10.dp, bottom = 9.dp))
                }
            }
            item {
                LatestCard(
                    modifier = Modifier.entrance(2),
                    note = latest,
                    columns = state.latestColumns,
                    onOpen = { latest?.let { onOpenNote(it.id) } },
                    onHistory = { scope.launch { listState.animateScrollToItem(OLDER_INDEX) } },
                )
            }
            listOf(R.string.library_yours to state.yours, R.string.library_received to state.received).forEach { (label, group) ->
                if (group.isNotEmpty()) {
                    item(key = "header-$label") {
                        Text(
                            stringResource(label),
                            style = HnType.bodySecondary,
                            color = HnColors.InkGrey,
                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp),
                        )
                    }
                    itemsIndexed(group, key = { _, it -> it.id }) { i, note ->
                        NoteRow(note, onClick = { onOpenNote(note.id) }, modifier = Modifier.entrance(3 + i.coerceAtMost(6)))
                    }
                }
            }
        }

        if (state.importing || state.message != null) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = insets.calculateTopPadding() + 8.dp, start = 20.dp, end = 20.dp)
                    .clip(HnDims.rowShape)
                    .background(HnColors.InkBlack)
                    .clickable(enabled = !state.importing, onClick = onDismissMessage)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.importing) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = HnColors.CardWhite, strokeWidth = 2.dp)
                }
                Text(
                    stringResource(state.message ?: R.string.import_progress),
                    style = HnType.bodySecondary,
                    color = HnColors.CardWhite,
                    modifier = Modifier.padding(start = if (state.importing) 12.dp else 0.dp),
                )
            }
        }

        ArcNavBar(
            outerLeft = NavAction(HeartnoteIcons.Shield, stringResource(R.string.cd_privacy), onPrivacy),
            innerLeft = NavAction(
                HeartnoteIcons.HandHeart,
                stringResource(R.string.cd_hold_latest),
                { latest?.let { onHold(it.id) } },
                enabled = latest != null,
            ),
            centre = NavAction(HeartnoteIcons.Pulse, stringResource(R.string.cd_record), onRecord),
            innerRight = NavAction(
                HeartnoteIcons.Share,
                stringResource(R.string.cd_share_latest),
                { latest?.let(onShare) },
                enabled = latest != null,
            ),
            outerRight = NavAction(HeartnoteIcons.Import, stringResource(R.string.cd_import), onImport),
            label = stringResource(R.string.nav_record),
            centreHalo = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp),
        )
        NavBarInsetFill(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TopRow(onPrivacy: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        HeartnoteLogo(44.dp, amount = 0.05f)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = HnType.cardTitle.copy(fontSize = HnType.screenTitle.fontSize), color = HnColors.InkBlack)
            Text(stringResource(R.string.library_tagline), style = HnType.caption, color = HnColors.InkGrey)
        }
        CircleIconButton(HeartnoteIcons.Shield, stringResource(R.string.cd_privacy), onPrivacy)
    }
}

@Composable
private fun LatestCard(
    note: NoteMeta?,
    columns: FloatArray?,
    onOpen: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The beat count counts up and the dot chart rises whenever the latest note changes.
    val count = rememberCountUp(note?.beatCount, note?.id)
    val rise = rememberRise(note?.id to (columns != null))
    val shownColumns = (columns ?: FloatArray(36)).let { c -> FloatArray(c.size) { c[it] * rise } }
    Column(
        modifier
            .fillMaxWidth()
            .clip(HnDims.cardShape)
            .background(HnColors.CardWhite)
            .warmGlow(centerX = 0.88f, centerY = 0.42f, radiusFraction = 0.45f, alpha = 0.07f)
            .clickable(enabled = note != null, role = Role.Button, onClick = onOpen)
            .padding(HnDims.cardPadding),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    note?.title ?: stringResource(R.string.app_name),
                    style = HnType.cardTitle,
                    color = HnColors.InkBlack,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note != null) {
                    Text(
                        noteDateLine(note),
                        style = HnType.cardSubtitle,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            CircleIconButton(HeartnoteIcons.History, stringResource(R.string.cd_all_notes), onHistory, outlined = true, enabled = note != null)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 36.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            DotMatrixNumber(
                value = count?.toString() ?: "--",
                dotColor = HnColors.InkBlack,
                digitHeight = 56.dp,
                contentDescription = note?.let { stringResource(R.string.library_beat_count_cd, it.beatCount) }
                    ?: stringResource(R.string.library_empty),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp)) {
            Text(
                stringResource(if (note != null) R.string.library_heartbeats else R.string.library_empty),
                style = HnType.bodySecondary,
                color = HnColors.InkGrey,
                modifier = Modifier.weight(1f),
            )
            if (note != null) Text(stringResource(R.string.library_beats), style = HnType.bodySecondary, color = HnColors.InkGrey)
        }
        DotBarChart(
            values = shownColumns,
            rows = 9,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Small DotRed heart beside the heading, beating at 70 BPM. */
@Composable
private fun BeatingHeart(modifier: Modifier = Modifier) {
    val scale by rememberHeartbeatScale(0.16f)
    Icon(
        HeartnoteIcons.HeartFilled,
        contentDescription = null,
        tint = HnColors.DotRed,
        modifier = modifier
            .size(28.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    )
}

@Composable
private fun NoteRow(note: NoteMeta, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(HnDims.rowShape)
            .background(HnColors.CardWhite)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.cd_open_note, note.title), onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(note.title, style = HnType.bodyPrimary, color = HnColors.InkBlack, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.library_row_detail, noteDateLine(note), formatDuration(note.durationMs)),
                style = HnType.caption,
                color = HnColors.InkGrey,
            )
        }
        Icon(HeartnoteIcons.ChevronRight, contentDescription = null, tint = HnColors.InkGrey, modifier = Modifier.size(18.dp))
    }
}

/** "Today • Nov 17, 2026", or "Received • Today • Nov 17, 2026" for imported notes. */
@Composable
private fun noteDateLine(note: NoteMeta): String {
    val date = formatNoteDate(LocalContext.current, note.createdAt)
    return if (note.isReceived) stringResource(R.string.library_received_line, date) else date
}

private val NAV_SPACE = 132.dp
private const val OLDER_INDEX = 3

private val previewNotes = listOf(
    NoteMeta("a", "Mum", System.currentTimeMillis(), 42_000, beatCount = 59, beatSource = "trace", smileCount = 2),
    NoteMeta("b", "Grandpa Joe", System.currentTimeMillis() - 86_400_000L, 31_000, beatCount = 44),
    NoteMeta("c", "you", System.currentTimeMillis() - 5 * 86_400_000L, 58_000, beatCount = 81),
)

@Preview(name = "Library", widthDp = 393, heightDp = 852)
@Composable
private fun LibraryPreview() {
    LibraryContent(
        LibraryUiState(loading = false, notes = previewNotes, latestColumns = previewDotValues(36)),
        {}, {}, {}, {}, {}, {}, {},
    )
}

@Preview(name = "Library — empty", widthDp = 393, heightDp = 852)
@Composable
private fun LibraryEmptyPreview() {
    LibraryContent(LibraryUiState(loading = false), {}, {}, {}, {}, {}, {}, {})
}
