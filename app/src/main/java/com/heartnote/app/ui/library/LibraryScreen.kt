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
import com.heartnote.app.share.ShareHelper
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

/** DESIGN.md § LibraryScreen (reference: left screen). F10. */
@Composable
fun LibraryScreen(
    onRecord: () -> Unit,
    onOpenNote: (String) -> Unit,
    onHold: (String) -> Unit,
    onPrivacy: () -> Unit,
    onAbout: () -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val shareTitle = stringResource(R.string.share_chooser_title)

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
        onShare = { id -> ShareHelper.share(context, viewModel.audioFile(id), shareTitle) },
        onPrivacy = onPrivacy,
        onAbout = onAbout,
    )
}

@Composable
internal fun LibraryContent(
    state: LibraryUiState,
    onRecord: () -> Unit,
    onOpenNote: (String) -> Unit,
    onHold: (String) -> Unit,
    onShare: (String) -> Unit,
    onPrivacy: () -> Unit,
    onAbout: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val latest = state.latest
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Box(
        Modifier
            .fillMaxSize()
            .background(HnColors.LightBg),
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
            item { TopRow(onPrivacy) }
            item {
                TwoToneHeading(
                    stringResource(R.string.library_heading_grey),
                    stringResource(R.string.library_heading_black),
                    Modifier.padding(top = 28.dp, bottom = 20.dp),
                )
            }
            item {
                LatestCard(
                    note = latest,
                    columns = state.latestColumns,
                    onOpen = { latest?.let { onOpenNote(it.id) } },
                    onHistory = { scope.launch { listState.animateScrollToItem(OLDER_INDEX) } },
                )
            }
            if (state.older.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.library_older),
                        style = HnType.bodySecondary,
                        color = HnColors.InkGrey,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp),
                    )
                }
                items(state.older, key = { it.id }) { note ->
                    NoteRow(note, onClick = { onOpenNote(note.id) })
                }
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
                { latest?.let { onShare(it.id) } },
                enabled = latest != null,
            ),
            outerRight = NavAction(HeartnoteIcons.Info, stringResource(R.string.cd_about), onAbout),
            label = stringResource(R.string.nav_record),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp),
        )
        NavBarInsetFill(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TopRow(onPrivacy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(HnColors.CardWhite),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(HnColors.DotRed),
            )
        }
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
private fun LatestCard(note: NoteMeta?, columns: FloatArray?, onOpen: () -> Unit, onHistory: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(HnDims.cardShape)
            .background(HnColors.CardWhite)
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
                        formatNoteDate(context, note.createdAt),
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
                value = note?.beatCount?.toString() ?: "--",
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
            values = columns ?: FloatArray(36),
            rows = 9,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NoteRow(note: NoteMeta, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
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
                stringResource(R.string.library_row_detail, formatNoteDate(context, note.createdAt), formatDuration(note.durationMs)),
                style = HnType.caption,
                color = HnColors.InkGrey,
            )
        }
        Icon(HeartnoteIcons.ChevronRight, contentDescription = null, tint = HnColors.InkGrey, modifier = Modifier.size(18.dp))
    }
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
        {}, {}, {}, {}, {}, {},
    )
}

@Preview(name = "Library — empty", widthDp = 393, heightDp = 852)
@Composable
private fun LibraryEmptyPreview() {
    LibraryContent(LibraryUiState(loading = false), {}, {}, {}, {}, {}, {})
}
