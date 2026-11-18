package com.heartnote.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.heartnote.app.R
import com.heartnote.app.audio.HeartbeatSynth
import com.heartnote.app.haptics.HeartbeatHaptics
import com.heartnote.app.ui.beat.BeatKind
import com.heartnote.app.ui.components.DotBarChart
import com.heartnote.app.ui.components.ParticleHeart
import com.heartnote.app.ui.components.ParticleSphere
import com.heartnote.app.ui.components.TwoToneHeading
import com.heartnote.app.ui.components.particles.isReducedMotion
import com.heartnote.app.ui.playback.DotChartData
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnDims
import com.heartnote.app.ui.theme.HnType
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGES = 3
private val STAGE = 240.dp
private val VISUAL = 200.dp

/**
 * ONBOARDING.md § Tour: 3 pages with generated data only (R-O7), Skip on every
 * page (R-O5). Vibrates only on page 2 and during "Feel a sample beat". No
 * permission is requested here.
 */
@Composable
fun TourScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val haptics = remember { HeartbeatHaptics(context) }
    val scope = rememberCoroutineScope()
    val sample = remember { SampleBeatPlayer(scope) { haptics.beat(it) } }
    val samplePlaying by sample.playing.collectAsState()
    val pager = rememberPagerState { PAGES }

    DisposableEffect(Unit) { onDispose { sample.stop() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { sample.stop() }
    LaunchedEffect(pager.settledPage) { if (pager.settledPage != PAGES - 1) sample.stop() }

    Column(
        Modifier
            .fillMaxSize()
            .background(HnColors.LightBg)
            .safeDrawingPadding()
            .padding(horizontal = HnDims.screenPadding),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { sample.stop(); onFinish() }, modifier = Modifier.heightIn(min = HnDims.minTouch)) {
                Text(stringResource(R.string.action_skip), style = HnType.bodyPrimary, color = HnColors.InkGrey)
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> TourPage(R.string.tour_1_grey, R.string.tour_1_black, R.string.tour_1_body) { RecordVisual() }
                1 -> TourPage(R.string.tour_2_grey, R.string.tour_2_black, R.string.tour_2_body) {
                    FeelVisual(active = pager.settledPage == 1, onBeat = haptics::beat)
                }
                else -> TourPage(R.string.tour_3_grey, R.string.tour_3_black, R.string.tour_3_body) {
                    ShareVisual(sample, samplePlaying)
                }
            }
        }
        PageDots(pager)
        val last = pager.currentPage == PAGES - 1
        if (last) {
            PrimaryPill(stringResource(R.string.tour_start), { sample.stop(); onFinish() }, Modifier.padding(top = 16.dp))
            TextButton(
                onClick = sample::toggle,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp)
                    .heightIn(min = HnDims.minTouch),
            ) {
                Text(
                    stringResource(if (samplePlaying) R.string.tour_sample_stop else R.string.tour_sample),
                    style = HnType.bodyPrimary,
                    color = HnColors.InkBlack,
                )
            }
        } else {
            PrimaryPill(
                stringResource(R.string.action_next),
                { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                Modifier.padding(top = 16.dp, bottom = 64.dp),
            )
        }
    }
}

@Composable
private fun TourPage(grey: Int, black: Int, body: Int, visual: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(STAGE)
                .clip(HnDims.cardShape)
                .background(HnColors.DarkBg),
            contentAlignment = Alignment.Center,
        ) { visual() }
        TwoToneHeading(stringResource(grey), stringResource(black), Modifier.fillMaxWidth().padding(top = 28.dp))
        Text(
            stringResource(body),
            style = HnType.bodyPrimary.copy(fontWeight = HnType.bodySecondary.fontWeight),
            color = HnColors.InkGrey,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

/** Page 1: small sphere pulsing at a generated 70 BPM with a slow fake breath. */
@Composable
private fun RecordVisual() {
    val start = remember { System.nanoTime() }
    ParticleSphere(
        beatEvents = remember { GeneratedBeats.flow() },
        breath = { sin((System.nanoTime() - start) / 1e9 * 2 * Math.PI / 4.0).toFloat() },
        modifier = Modifier.size(VISUAL),
        interactive = false,
    )
}

/** Page 2: a dot row filling up, a column flash and a vibration on every generated beat. */
@Composable
private fun FeelVisual(active: Boolean, onBeat: (BeatKind) -> Unit) {
    val context = LocalContext.current
    val reduced = remember { isReducedMotion(context) }
    val beats = remember { GeneratedBeats.beatsMs(DotChartData.ROW_MS) }
    val values = remember {
        FloatArray(DotChartData.ROW_COLUMNS) { c ->
            val t0 = c * DotChartData.COLUMN_MS
            val hasBeat = beats.any { it in t0 until t0 + DotChartData.COLUMN_MS }
            if (hasBeat) 0.85f + (c % 3) * 0.05f else 0.2f + (c % 4) * 0.04f
        }
    }
    var progress by remember { mutableFloatStateOf(if (reduced) 1f else 0f) }
    var flashColumn by remember { mutableIntStateOf(-1) }
    var flashKey by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val ticks = beats.flatMap { listOf(it to BeatKind.LUB, it + HeartbeatSynth.dubDelayMs(GeneratedBeats.rrMs) to BeatKind.DUB) }
            val start = System.currentTimeMillis()
            var cursor = 0
            var lastLoop = 0L
            while (true) {
                val elapsed = System.currentTimeMillis() - start
                val loop = elapsed / DotChartData.ROW_MS
                if (loop != lastLoop) { cursor = 0; lastLoop = loop }
                val t = elapsed % DotChartData.ROW_MS
                if (!reduced) progress = t.toFloat() / DotChartData.ROW_MS
                while (cursor < ticks.size && ticks[cursor].first <= t) {
                    val (time, kind) = ticks[cursor++]
                    onBeat(kind)
                    if (kind == BeatKind.LUB) {
                        flashColumn = (time / DotChartData.COLUMN_MS).toInt()
                        flashKey++
                    }
                }
                delay(16)
            }
        }
    }
    Box(Modifier.padding(16.dp).clip(HnDims.rowShape).background(HnColors.CardWhite).padding(12.dp)) {
        DotBarChart(
            values = values,
            rows = 9,
            progress = progress,
            highlightIndex = flashColumn.takeIf { it >= 0 },
            flashKey = flashKey,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Page 3: the particle heart at 70 BPM; follows the sample beat while it plays. */
@Composable
private fun ShareVisual(sample: SampleBeatPlayer, playing: Boolean) {
    val generated = remember { GeneratedBeats.flow() }
    Box(contentAlignment = Alignment.BottomCenter) {
        ParticleHeart(beatEvents = if (playing) sample.events else generated, modifier = Modifier.size(VISUAL))
        if (playing) {
            Text(
                stringResource(R.string.tour_sample_label),
                style = HnType.caption,
                color = HnColors.OnDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun PageDots(pager: PagerState) {
    val description = stringResource(R.string.tour_page_cd, pager.currentPage + 1, PAGES)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(PAGES) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (i == pager.currentPage) HnColors.DotRed else HnColors.DotIdle),
            )
        }
    }
}

@Preview(name = "Tour", widthDp = 393, heightDp = 852)
@Composable
private fun TourPreview() {
    TourScreen(onFinish = {})
}
