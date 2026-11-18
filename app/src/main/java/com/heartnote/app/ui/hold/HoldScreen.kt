package com.heartnote.app.ui.hold

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartnote.app.R
import com.heartnote.app.ui.share.NoteShareSheet
import com.heartnote.app.ui.share.ShareViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.heartnote.app.ui.beat.BeatBus
import com.heartnote.app.ui.beat.BeatKind
import com.heartnote.app.ui.components.CircleIconButton
import com.heartnote.app.ui.components.DotMatrixNumber
import com.heartnote.app.ui.components.HeartnoteIcons
import com.heartnote.app.ui.components.ParticleHeart
import com.heartnote.app.ui.playback.PlaybackUiState
import com.heartnote.app.ui.playback.PlaybackViewModel
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnDims
import com.heartnote.app.ui.theme.HnType

/**
 * DESIGN.md § HoldScreen (reference: ref-heart.png — background, layout,
 * floating card and nav; the heart is ParticleHeart by design). F11.
 * Immersive, screen kept on, plays the note with sound + haptics.
 */
@Composable
fun HoldScreen(onBack: () -> Unit, viewModel: PlaybackViewModel = viewModel()) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_chooser_title)
    val scope = rememberCoroutineScope()
    val meta by remember { derivedStateOf { state.value.meta } }
    val isPlaying by remember { derivedStateOf { state.value.player.isPlaying } }

    val share: ShareViewModel = viewModel()
    NoteShareSheet(share)
    ImmersiveKeepScreenOn()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }
    LaunchedEffect(meta != null) { if (meta != null) viewModel.play() }

    // Gradient brightens 15% for 180 ms on every lub.
    val glow = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        BeatBus.events.collect { e ->
            if (e.noteTimeMs != null && e.kind == BeatKind.LUB) {
                glow.snapTo(GLOW_BOOST)
                glow.animateTo(0f, tween(GLOW_MS))
            }
        }
    }

    Box(Modifier.fillMaxSize().background(HnColors.DarkBg)) {
        Canvas(Modifier.fillMaxSize()) {
            val b = 1f + glow.value
            fun bright(c: Color) = Color((c.red * b).coerceAtMost(1f), (c.green * b).coerceAtMost(1f), (c.blue * b).coerceAtMost(1f))
            drawRect(
                Brush.radialGradient(
                    0f to bright(HnColors.HoldGlow),
                    0.45f to bright(HnColors.HoldDeep),
                    1f to HnColors.DarkBg,
                    center = Offset(size.width / 2, 0f),
                    radius = size.height,
                ),
            )
        }

        // Top: back + title + subtitle
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(horizontal = HnDims.screenPadding, vertical = 16.dp),
        ) {
            CircleIconButton(
                HeartnoteIcons.Back,
                stringResource(R.string.cd_back),
                onBack,
                dark = true,
                containerColor = Color.White.copy(alpha = BACK_OVERLAY),
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    meta?.title.orEmpty(),
                    style = HnType.holdTitle,
                    color = HnColors.OnDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.hold_subtitle),
                    style = HnType.bodySecondary,
                    color = HnColors.OnDark.copy(alpha = 0.7f),
                )
            }
        }

        // Centre: particle heart + floating card overlapping its right side
        BoxWithConstraints(Modifier.align(Alignment.Center).fillMaxWidth()) {
            val w = maxWidth
            ParticleHeart(beatEvents = BeatBus.events, modifier = Modifier.size(w))
            HoldCard(
                state = state,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = w * 0.24f, y = w * 0.14f),
            )
        }

        // Bottom: dark pill nav
        HoldNav(
            isPlaying = isPlaying,
            onBack = onBack,
            onToggle = viewModel::togglePlay,
            onReplay = { viewModel.seekTo(0); viewModel.play() },
            onShare = { meta?.let(share::open) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
        )
    }
}

@Composable
private fun HoldCard(state: State<PlaybackUiState>, modifier: Modifier) {
    val signals by remember { derivedStateOf { state.value.signals } }
    val trace = remember(signals) {
        val raw = signals?.raw?.bpTrace.orEmpty()
        if (raw.isNotEmpty()) {
            LongArray(raw.size) { raw[it].tMs } to FloatArray(raw.size) { raw[it].v }
        } else {
            pulseShapeFromBeats(signals?.beatsMs.orEmpty(), signals?.durationMs ?: 0L)
        }
    }
    val beats = remember(signals) { signals?.beatsMs?.toLongArray() ?: LongArray(0) }
    val played by remember(beats) {
        derivedStateOf {
            val pos = state.value.player.positionMs
            val idx = beats.binarySearch(pos)
            if (idx >= 0) idx + 1 else -idx - 1
        }
    }
    Column(
        modifier
            .size(CARD_WIDTH, CARD_HEIGHT)
            .clip(RoundedCornerShape(20.dp))
            .background(HnColors.CardWhite)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(stringResource(R.string.hold_card_title), style = HnType.caption, color = HnColors.InkBlack)
        // 2-second scrolling mini trace ending at the playback position.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(vertical = 4.dp),
        ) {
            val (t, v) = trace
            if (t.size < 2) return@Canvas
            val end = state.value.player.positionMs
            val start = end - TRACE_WINDOW_MS
            var from = t.binarySearch(start).let { if (it < 0) -it - 1 else it }
            val to = t.binarySearch(end).let { if (it < 0) -it - 1 else it }.coerceAtMost(t.size)
            if (to - from < 2) return@Canvas
            var lo = Float.MAX_VALUE
            var hi = -Float.MAX_VALUE
            for (i in from until to) { lo = minOf(lo, v[i]); hi = maxOf(hi, v[i]) }
            val range = (hi - lo).takeIf { it > 1e-6f } ?: 1f
            val path = Path()
            while (from < to) {
                val x = (t[from] - start).toFloat() / TRACE_WINDOW_MS * size.width
                val y = size.height * (1f - (v[from] - lo) / range)
                if (path.isEmpty) path.moveTo(x, y) else path.lineTo(x, y)
                from++
            }
            drawPath(path, HnColors.DotRed, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            DotMatrixNumber(
                played.toString(),
                HnColors.InkBlack,
                22.dp,
                contentDescription = stringResource(R.string.hold_beat_count_cd, played),
            )
            Text(
                stringResource(R.string.playback_beats),
                style = HnType.caption,
                color = HnColors.InkGrey,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun HoldNav(
    isPlaying: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onReplay: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier
            .clip(HnDims.pill)
            .background(HnColors.DarkSurface)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavIcon(HeartnoteIcons.Back, stringResource(R.string.cd_back), onBack, active = false)
        NavIcon(
            if (isPlaying) HeartnoteIcons.Pause else HeartnoteIcons.Play,
            stringResource(if (isPlaying) R.string.playback_pause else R.string.playback_play),
            onToggle,
            active = true,
        )
        NavIcon(HeartnoteIcons.Replay, stringResource(R.string.hold_replay), onReplay, active = false)
        NavIcon(HeartnoteIcons.Share, stringResource(R.string.cd_share), onShare, active = false)
    }
}

@Composable
private fun NavIcon(icon: ImageVector, description: String, onClick: () -> Unit, active: Boolean) {
    Box(
        Modifier
            .size(HnDims.circleButton)
            .clip(CircleShape)
            .background(if (active) HnColors.CardWhite else Color.Transparent)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (active) HnColors.InkBlack else HnColors.OnDark,
            modifier = Modifier.size(HnDims.circleIcon),
        )
    }
}

/** Hide system bars (swipe to peek) and keep the screen on while Hold is shown. */
@Composable
private fun ImmersiveKeepScreenOn() {
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(activity, view) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
}

/** 30 Hz pulse-like curve with a peak on every beat (received notes carry no trace). */
private fun pulseShapeFromBeats(beats: List<Long>, durationMs: Long): Pair<LongArray, FloatArray> {
    if (beats.isEmpty() || durationMs <= 0) return LongArray(0) to FloatArray(0)
    val n = (durationMs / 33).toInt() + 1
    val t = LongArray(n) { it * 33L }
    val sorted = beats.sorted()
    var j = 0
    val v = FloatArray(n) { i ->
        while (j < sorted.size - 1 && sorted[j + 1] <= t[i]) j++
        val dt = (t[i] - sorted[j]).toFloat() / 1000f
        if (dt < 0f) 0f else kotlin.math.exp(-dt * dt / 0.004f) + 0.3f * kotlin.math.exp(-(dt - 0.33f) * (dt - 0.33f) / 0.006f)
    }
    return t to v
}

private const val GLOW_BOOST = 0.15f
private const val GLOW_MS = 180
private const val BACK_OVERLAY = 0.4f
private const val TRACE_WINDOW_MS = 2_000L
private val CARD_WIDTH = 150.dp
private val CARD_HEIGHT = 120.dp
