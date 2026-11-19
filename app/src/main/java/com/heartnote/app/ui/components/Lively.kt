package com.heartnote.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.heartnote.app.R
import com.heartnote.app.ui.components.particles.isReducedMotion
import com.heartnote.app.ui.theme.HnColors
import kotlinx.coroutines.delay

/*
 * "Lively" helpers for the opening and home screens: motion only, using the
 * existing colour tokens (alpha variations of DotRed). Every effect is off
 * when the system's animator scale is 0 (reduced motion).
 */

private const val BEAT_MS = 857 // 70 BPM

@Composable
private fun reducedMotion(): Boolean {
    val context = LocalContext.current
    // Previews render one still frame: show the resting state, not frame 0 of an entrance.
    val inspection = LocalInspectionMode.current
    return remember { inspection || isReducedMotion(context) }
}

/** Lub-dub scale at 70 BPM: 1 → 1+[amount] (lub) → 1 → 1+[amount]/2 (dub) → rest. */
@Composable
fun rememberHeartbeatScale(amount: Float = 0.06f): State<Float> {
    if (reducedMotion()) return remember { mutableFloatStateOf(1f) }
    return rememberInfiniteTransition(label = "heartbeat").animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = heartbeatSpec(1f, amount),
        label = "scale",
    )
}

private fun heartbeatSpec(base: Float, amount: Float): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
    keyframes {
        durationMillis = BEAT_MS
        base at 0
        base + amount at 90 using FastOutSlowInEasing
        base at 310 using FastOutSlowInEasing
        base + amount / 2 at 380 using FastOutSlowInEasing
        base at 560
    },
    RepeatMode.Restart,
)

/**
 * The Heartnote logo (the app icon art). [beating] makes it pulse at 70 BPM;
 * [glow] adds a soft DotRed halo behind it that swells with each lub.
 */
@Composable
fun HeartnoteLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    beating: Boolean = true,
    glow: Boolean = false,
    amount: Float = 0.06f,
) {
    val scale by rememberHeartbeatScale(if (beating) amount else 0f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (glow) {
            Box(
                Modifier
                    .requiredSize(size * 1.9f) // may overflow the logo box: it is only light
                    .graphicsLayer {
                        val s = 1f + (scale - 1f) * 2.5f
                        scaleX = s
                        scaleY = s
                        alpha = 0.55f + (scale - 1f) * 6f
                    }
                    .background(
                        Brush.radialGradient(listOf(HnColors.DotRed.copy(alpha = 0.35f), Color.Transparent)),
                    ),
            )
        }
        Image(
            painter = painterResource(R.drawable.heartnote_logo),
            contentDescription = stringResource(R.string.cd_logo),
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

/**
 * Fade + rise in, [index] × 70 ms after the screen appears (staggered entrance).
 * Plays once: the saved flag survives list rows scrolling away and back-stack returns.
 */
fun Modifier.entrance(index: Int): Modifier = composed {
    val reduced = reducedMotion()
    var played by rememberSaveable { mutableStateOf(reduced) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    val rise = with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        delay(index * 70L)
        progress.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        played = true
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}

/** A DotRed ring that ripples out from behind a round button on every lub. */
fun Modifier.heartbeatHalo(diameter: Dp): Modifier = composed {
    if (reducedMotion()) return@composed this
    val ripple by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BEAT_MS * 2, easing = LinearEasing), RepeatMode.Restart),
        label = "ripple",
    )
    val strokePx = with(LocalDensity.current) { 3.dp.toPx() }
    val basePx = with(LocalDensity.current) { diameter.toPx() / 2 }
    drawBehind {
        // Two ripples per cycle (lub, dub), each growing and fading.
        for (offset in listOf(0f, 0.18f)) {
            val p = ((ripple - offset + 1f) % 1f)
            if (p > 0.6f) continue
            val t = p / 0.6f
            drawCircle(
                color = HnColors.DotRed.copy(alpha = (1f - t) * if (offset == 0f) 0.45f else 0.25f),
                radius = basePx * (1f + 0.45f * t),
                center = Offset(size.width / 2, size.height / 2),
                style = Stroke(width = strokePx * (1f - t * 0.5f)),
            )
        }
    }
}

/** 0 → 1 over 700 ms whenever [key] changes (e.g. a dot chart rising into place). */
@Composable
fun rememberRise(key: Any?): Float {
    val reduced = reducedMotion()
    val p = remember(key) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key) {
        if (!reduced) p.animateTo(1f, tween(700, delayMillis = 150, easing = FastOutSlowInEasing))
    }
    return p.value
}

/** Counts up from 0 to [target] over 900 ms whenever [key] changes; null stays null. */
@Composable
fun rememberCountUp(target: Int?, key: Any?): Int? {
    val reduced = reducedMotion()
    val v = remember(key) { Animatable(if (reduced) (target ?: 0).toFloat() else 0f) }
    LaunchedEffect(key, target) {
        if (target == null) return@LaunchedEffect
        if (reduced) v.snapTo(target.toFloat())
        else v.animateTo(target.toFloat(), tween(900, delayMillis = 150, easing = FastOutSlowInEasing))
    }
    return target?.let { kotlin.math.round(v.value).toInt() }
}

/** Soft DotRed light pooled on a light surface (existing colour, alpha only). */
fun Modifier.warmGlow(centerX: Float, centerY: Float, radiusFraction: Float, alpha: Float = 0.10f): Modifier = drawBehind {
    val r = size.maxDimension * radiusFraction
    val c = Offset(size.width * centerX, size.height * centerY)
    drawCircle(
        brush = Brush.radialGradient(listOf(HnColors.DotRed.copy(alpha = alpha), Color.Transparent), center = c, radius = r),
        radius = r,
        center = c,
    )
}

@Preview(name = "HeartnoteLogo")
@Composable
private fun HeartnoteLogoPreview() {
    Box(Modifier.background(HnColors.LightBg).size(220.dp), contentAlignment = Alignment.Center) {
        HeartnoteLogo(120.dp, glow = true)
    }
}
