package com.heartnote.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnType

enum class ProgressStyle { DETERMINATE, PULSING, INDETERMINATE }

/**
 * DESIGN.md § ProgressLine: 175 dp × 4 dp DarkTrack track, SignalRed fill,
 * label below in 15 sp OnDark. PULSING = full bar breathing in alpha (Flushing),
 * INDETERMINATE = a sliding segment (Processing).
 */
@Composable
fun ProgressLine(
    fraction: Float,
    label: String,
    modifier: Modifier = Modifier,
    style: ProgressStyle = ProgressStyle.DETERMINATE,
) {
    val transition = rememberInfiniteTransition(label = "progress")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(WIDTH, HEIGHT)
                .semantics {
                    if (style == ProgressStyle.DETERMINATE) {
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
                    } else {
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    }
                },
        ) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(HnColors.DarkTrack, cornerRadius = r)
            when (style) {
                ProgressStyle.DETERMINATE -> {
                    val w = size.width * fraction.coerceIn(0f, 1f)
                    if (w > 0f) drawRoundRect(HnColors.SignalRed, size = Size(w, size.height), cornerRadius = r)
                }
                ProgressStyle.PULSING -> {
                    val alpha = 0.45f + 0.55f * (0.5f + 0.5f * kotlin.math.cos(phase * 2 * Math.PI.toFloat()))
                    drawRoundRect(HnColors.SignalRed, cornerRadius = r, alpha = alpha)
                }
                ProgressStyle.INDETERMINATE -> {
                    val seg = size.width * 0.3f
                    val x = (size.width + seg) * phase - seg
                    val left = x.coerceAtLeast(0f)
                    val right = (x + seg).coerceAtMost(size.width)
                    if (right > left) {
                        drawRoundRect(HnColors.SignalRed, Offset(left, 0f), Size(right - left, size.height), r)
                    }
                }
            }
        }
        Text(label, style = HnType.progressLabel, color = HnColors.OnDark, modifier = Modifier.padding(top = 10.dp))
    }
}

private val WIDTH = 175.dp
private val HEIGHT = 4.dp

@Preview(name = "ProgressLine states")
@Composable
private fun ProgressLinePreview() {
    Column(Modifier.background(HnColors.DarkBg).padding(16.dp)) {
        ProgressLine(0.12f, "12%")
        ProgressLine(1f, "Finishing", Modifier.padding(top = 16.dp), ProgressStyle.PULSING)
        ProgressLine(0f, "Saving", Modifier.padding(top = 16.dp), ProgressStyle.INDETERMINATE)
    }
}
