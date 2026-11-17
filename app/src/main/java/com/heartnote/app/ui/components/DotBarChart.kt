package com.heartnote.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.heartnote.app.ui.theme.HnColors
import kotlin.math.roundToInt
import kotlin.random.Random

/** Height of a [rows]-row DotBarChart. */
fun dotBarChartHeight(rows: Int): Dp = DOT * rows + GAP * (rows - 1)

/**
 * DESIGN.md § COMPONENTS: `rows` × `values.size` dots (4.5 dp dot, 2.5 dp
 * vertical gap; columns spread across the width). Every dot is DotIdle; the
 * bottom round(value × rows) dots of each column are DotRed. Dots outside a
 * stadium (corner radius = height / 2) are not drawn. Columns after [progress]
 * are DotRed at 35%. [highlightIndex] scales its column 1.25× for 160 ms each
 * time [flashKey] changes (beat flash).
 */
@Composable
fun DotBarChart(
    values: FloatArray,
    rows: Int,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    highlightIndex: Int? = null,
    flashKey: Any? = null,
) {
    val flash = remember { Animatable(1f) }
    LaunchedEffect(flashKey) {
        if (flashKey != null && highlightIndex != null) {
            flash.snapTo(FLASH_SCALE)
            flash.animateTo(1f, tween(FLASH_MS))
        }
    }

    Canvas(modifier.height(dotBarChartHeight(rows))) {
        val cols = values.size
        if (cols == 0) return@Canvas
        val dot = DOT.toPx()
        val pitchY = (DOT + GAP).toPx()
        val pitchX = size.width / cols
        val radius = dot / 2
        val h = size.height
        val corner = h / 2
        val progressCol = progress?.let { (it.coerceIn(0f, 1f) * cols) }

        for (c in 0 until cols) {
            val cx = (c + 0.5f) * pitchX
            val red = (values[c].coerceIn(0f, 1f) * rows).roundToInt()
            val played = progressCol == null || c < progressCol
            val scale = if (c == highlightIndex) flash.value else 1f
            for (r in 0 until rows) {
                val cy = radius + r * pitchY
                if (!insideStadium(cx, cy, size.width, h, corner)) continue
                val isRed = r >= rows - red
                val color = if (isRed) HnColors.DotRed else HnColors.DotIdle
                val alpha = if (isRed && !played) UNPLAYED_ALPHA else 1f
                drawCircle(color, radius * scale, Offset(cx, cy), alpha = alpha)
            }
        }
    }
}

private val DOT = 4.5.dp
private val GAP = 2.5.dp
private const val UNPLAYED_ALPHA = 0.35f
private const val FLASH_SCALE = 1.25f
private const val FLASH_MS = 160

private fun insideStadium(x: Float, y: Float, w: Float, h: Float, r: Float): Boolean {
    val cx = when {
        x < r -> r
        x > w - r -> w - r
        else -> return true
    }
    val dx = x - cx
    val dy = y - h / 2
    return dx * dx + dy * dy <= r * r
}

/** Spiky beat-envelope-like sample data for previews. */
internal fun previewDotValues(columns: Int, seed: Int = 3): FloatArray {
    val rnd = Random(seed)
    return FloatArray(columns) { i ->
        val beat = if (i % 4 == 1) 0.45f + rnd.nextFloat() * 0.5f else 0.2f + rnd.nextFloat() * 0.25f
        beat.coerceIn(0.15f, 1f)
    }
}

@Preview(name = "DotBarChart Library 9×36", widthDp = 320)
@Composable
private fun DotBarChartLibraryPreview() {
    Column(Modifier.background(HnColors.CardWhite).padding(18.dp)) {
        DotBarChart(previewDotValues(36), rows = 9, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "DotBarChart Playback rows with progress", widthDp = 320)
@Composable
private fun DotBarChartPlaybackPreview() {
    Column(Modifier.background(HnColors.CardWhite).padding(18.dp)) {
        DotBarChart(previewDotValues(40, 1), rows = 9, progress = 1f, modifier = Modifier.width(284.dp))
        DotBarChart(
            previewDotValues(40, 2),
            rows = 9,
            progress = 0.45f,
            highlightIndex = 17,
            modifier = Modifier.width(284.dp).padding(top = 14.dp),
        )
        DotBarChart(previewDotValues(40, 4), rows = 9, progress = 0f, modifier = Modifier.width(284.dp).padding(top = 14.dp))
    }
}
