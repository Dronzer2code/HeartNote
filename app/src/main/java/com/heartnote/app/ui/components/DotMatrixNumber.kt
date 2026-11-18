package com.heartnote.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.heartnote.app.ui.theme.HnColors
import kotlin.math.max

/**
 * DESIGN.md § COMPONENTS: digits 0–9 and '-' on a 5×7 dot grid, glyph gap 1
 * column, dot diameter 0.78 × cell, only "on" dots drawn. Value changes
 * cross-fade per dot over 120 ms. Strings of different lengths align right.
 */
@Composable
fun DotMatrixNumber(
    value: String,
    dotColor: Color,
    digitHeight: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String = value,
) {
    var shown by remember { mutableStateOf(value) }
    var previous by remember { mutableStateOf(value) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(value) {
        if (value != shown) {
            previous = shown
            shown = value
            progress.snapTo(0f)
            progress.animateTo(1f, tween(CROSSFADE_MS))
        }
    }

    val columns = max(shown.length, previous.length)
    val cell = digitHeight / ROWS
    val width = cell * (columns * GLYPH_COLS + (columns - 1).coerceAtLeast(0))

    Canvas(
        modifier = modifier
            .size(width, digitHeight)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val cellPx = size.height / ROWS
        val radius = cellPx * DOT_FRACTION / 2
        val p = progress.value
        for (slot in 0 until columns) {
            val newGlyph = glyphAt(shown, slot, columns)
            val oldGlyph = glyphAt(previous, slot, columns)
            val x0 = slot * (GLYPH_COLS + 1) * cellPx
            for (row in 0 until ROWS) for (col in 0 until GLYPH_COLS) {
                val on = newGlyph.isOn(row, col)
                val was = oldGlyph.isOn(row, col)
                val alpha = when {
                    on && was -> 1f
                    on -> p
                    was -> 1f - p
                    else -> 0f
                }
                if (alpha > 0f) {
                    drawCircle(
                        color = dotColor,
                        radius = radius,
                        center = Offset(x0 + (col + 0.5f) * cellPx, (row + 0.5f) * cellPx),
                        alpha = alpha,
                    )
                }
            }
        }
    }
}

/** Width of [length] glyphs at [digitHeightPx] (5 columns + 1 gap per glyph). */
fun dotMatrixWidth(length: Int, digitHeightPx: Float): Float =
    digitHeightPx / ROWS * (length * GLYPH_COLS + (length - 1).coerceAtLeast(0))

/** Same glyphs on a plain android Canvas (video export frames). */
fun drawDotMatrix(
    canvas: android.graphics.Canvas,
    value: String,
    left: Float,
    top: Float,
    digitHeightPx: Float,
    paint: android.graphics.Paint,
) {
    val cell = digitHeightPx / ROWS
    val radius = cell * DOT_FRACTION / 2
    value.forEachIndexed { slot, ch ->
        val glyph = GLYPHS[ch] ?: return@forEachIndexed
        val x0 = left + slot * (GLYPH_COLS + 1) * cell
        for (row in 0 until ROWS) for (col in 0 until GLYPH_COLS) {
            if (glyph.isOn(row, col)) canvas.drawCircle(x0 + (col + 0.5f) * cell, top + (row + 0.5f) * cell, radius, paint)
        }
    }
}

private const val ROWS = 7
private const val GLYPH_COLS = 5
private const val DOT_FRACTION = 0.78f
private const val CROSSFADE_MS = 120

private fun glyphAt(text: String, slot: Int, columns: Int): Glyph {
    val index = slot - (columns - text.length)
    return if (index in text.indices) GLYPHS[text[index]] ?: Glyph.BLANK else Glyph.BLANK
}

@JvmInline
private value class Glyph(val rows: IntArray) {
    fun isOn(row: Int, col: Int) = rows[row] shr (GLYPH_COLS - 1 - col) and 1 == 1

    companion object {
        val BLANK = Glyph(IntArray(ROWS))
        fun of(vararg r: String) = Glyph(IntArray(ROWS) { r[it].toInt(2) })
    }
}

private val GLYPHS = mapOf(
    '0' to Glyph.of("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    '1' to Glyph.of("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    '2' to Glyph.of("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
    '3' to Glyph.of("01110", "10001", "00001", "00110", "00001", "10001", "01110"),
    '4' to Glyph.of("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
    '5' to Glyph.of("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
    '6' to Glyph.of("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
    '7' to Glyph.of("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    '8' to Glyph.of("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    '9' to Glyph.of("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
    '-' to Glyph.of("00000", "00000", "00000", "01110", "00000", "00000", "00000"),
)

@Preview(name = "DotMatrixNumber sizes")
@Composable
private fun DotMatrixNumberPreview() {
    Column(Modifier.background(HnColors.CardWhite).padding(16.dp)) {
        DotMatrixNumber("89", HnColors.InkBlack, 56.dp)
        DotMatrixNumber("85", HnColors.InkBlack, 26.dp, Modifier.padding(top = 12.dp))
        DotMatrixNumber("--", HnColors.InkBlack, 56.dp, Modifier.padding(top = 12.dp))
    }
}

@Preview(name = "DotMatrixNumber dark (Record)")
@Composable
private fun DotMatrixNumberDarkPreview() {
    Column(Modifier.background(HnColors.DarkBg).padding(16.dp)) {
        DotMatrixNumber("85", HnColors.OnDark, 72.dp)
    }
}
