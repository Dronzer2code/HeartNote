package com.heartnote.app.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.heartnote.app.R
import com.heartnote.app.ui.components.HeartModel
import com.heartnote.app.ui.components.HeartPoints
import com.heartnote.app.ui.components.dotMatrixWidth
import com.heartnote.app.ui.components.drawDotMatrix
import com.heartnote.app.ui.components.particles.ParticleEngine
import com.heartnote.app.ui.theme.HnColors

/**
 * One frame of the Heartnote video (SHARE_AND_IMPORT.md § Frame content):
 * HoldScreen look rendered offscreen at 720 × 1280 — radial red gradient
 * with a flash on each lub, the particle heart beating on beatsMs, title,
 * dot-matrix beat counter, footer, and an end card in the last 2 s.
 * No blur RenderEffect: the glow is a soft radial-gradient disc.
 * No pulse numbers (PRD C1): the counter counts beats played.
 */
class HeartnoteVideoFrame(
    context: Context,
    private val title: String,
    private val beatsMs: LongArray,
    private val durationMs: Long,
) {
    private val engine = ParticleEngine(HeartModel(HeartPoints.get())).apply {
        widthPx = WIDTH.toFloat()
        heightPx = WIDTH.toFloat()
    }
    private val semibold: Typeface = ResourcesCompat.getFont(context, R.font.inter_semibold) ?: Typeface.DEFAULT_BOLD
    private val regular: Typeface = ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.DEFAULT
    private val footer = context.getString(R.string.video_footer)
    private val beatsLabel = context.getString(R.string.playback_beats)
    private val wordmark = context.getString(R.string.app_name)

    private val titlePaint = text(semibold, 46f, HnColors.OnDark)
    private val captionPaint = text(regular, 28f, HnColors.OnDark.copy(alpha = 0.7f))
    private val footerPaint = text(regular, 27f, HnColors.OnDark.copy(alpha = 0.85f))
    private val endPaint = text(semibold, 52f, HnColors.OnDark)
    private val wordPaint = text(semibold, 26f, HnColors.OnDark)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HnColors.OnDark.toArgb() }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HnColors.DotRed.toArgb() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heartArgb = HnColors.HeartRed.toArgb()

    fun draw(canvas: Canvas, timeMs: Long) {
        val boost = lubFlash(timeMs)

        // Background: HoldGlow → HoldDeep (45%) → black, centred at the top.
        fill.shader = RadialGradient(
            WIDTH / 2f, 0f, HEIGHT.toFloat(),
            intArrayOf(bright(HnColors.HoldGlow, boost), bright(HnColors.HoldDeep, boost), HnColors.DarkBg.toArgb()),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), fill)

        // Soft glow disc behind the heart (replaces the on-screen blur).
        val glowR = 330f * (1f + boost)
        fill.shader = RadialGradient(
            WIDTH / 2f, HEART_CENTER_Y, glowR,
            intArrayOf(HnColors.HeartRed.copy(alpha = 0.35f + boost).toArgb(), android.graphics.Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(WIDTH / 2f, HEART_CENTER_Y, glowR, fill)
        fill.shader = null

        // Particle heart on a 720 px square centred at HEART_CENTER_Y.
        canvas.save()
        canvas.translate(0f, HEART_CENTER_Y - WIDTH / 2f)
        engine.renderAt(timeMs, beatsMs, canvas, heartArgb, DENSITY)
        canvas.restore()

        // Title.
        centered(canvas, title, titlePaint, 150f)

        // Beat counter (beats played so far) + caption.
        val played = countUpTo(timeMs).toString()
        val digitH = 64f
        val w = dotMatrixWidth(played.length, digitH)
        drawDotMatrix(canvas, played, (WIDTH - w) / 2f, 1000f, digitH, dotPaint)
        centered(canvas, beatsLabel, captionPaint, 1105f)

        // Footer for the whole video, larger end card in the last 2 s.
        val endAlpha = ((timeMs - (durationMs - END_CARD_MS)).toFloat() / 400f).coerceIn(0f, 1f)
        if (endAlpha < 1f) {
            footerPaint.alpha = ((1f - endAlpha) * 0.85f * 255).toInt()
            centered(canvas, footer, footerPaint, 1190f)
            drawWordmark(canvas, 1240f, 1f - endAlpha, 1f)
        }
        if (endAlpha > 0f) {
            fill.color = android.graphics.Color.argb((endAlpha * 200).toInt(), 0, 0, 0)
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), fill)
            endPaint.alpha = (endAlpha * 255).toInt()
            val layout = StaticLayout.Builder.obtain(footer, 0, footer.length, endPaint, WIDTH - 120)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
            canvas.save()
            canvas.translate(60f, HEIGHT / 2f - layout.height / 2f)
            layout.draw(canvas)
            canvas.restore()
            drawWordmark(canvas, HEIGHT / 2f + layout.height / 2f + 90f, endAlpha, 1.5f)
        }
    }

    private fun drawWordmark(canvas: Canvas, baseline: Float, alpha: Float, scale: Float) {
        wordPaint.textSize = 26f * scale
        wordPaint.alpha = (alpha * 255).toInt()
        markPaint.alpha = (alpha * 255).toInt()
        val textW = wordPaint.measureText(wordmark)
        val dotR = 9f * scale
        val total = dotR * 2 + 12f * scale + textW
        val x = (WIDTH - total) / 2f
        canvas.drawCircle(x + dotR, baseline - wordPaint.textSize * 0.35f, dotR, markPaint)
        canvas.drawText(wordmark, x + dotR * 2 + 12f * scale, baseline, wordPaint)
    }

    private fun centered(canvas: Canvas, s: String, paint: TextPaint, baseline: Float) {
        val text = android.text.TextUtils.ellipsize(s, paint, WIDTH - 80f, android.text.TextUtils.TruncateAt.END).toString()
        canvas.drawText(text, (WIDTH - paint.measureText(text)) / 2f, baseline, paint)
    }

    /** 0.15 just after a lub, fading over 180 ms (DESIGN.md § HoldScreen). */
    private fun lubFlash(timeMs: Long): Float {
        val idx = beatsMs.binarySearch(timeMs).let { if (it >= 0) it else -it - 2 }
        if (idx < 0) return 0f
        val age = timeMs - beatsMs[idx]
        return if (age in 0 until FLASH_MS) FLASH * (1f - age.toFloat() / FLASH_MS) else 0f
    }

    private fun countUpTo(timeMs: Long): Int = beatsMs.binarySearch(timeMs).let { if (it >= 0) it + 1 else -it - 1 }

    private fun bright(c: Color, boost: Float): Int {
        val b = 1f + boost
        return Color((c.red * b).coerceAtMost(1f), (c.green * b).coerceAtMost(1f), (c.blue * b).coerceAtMost(1f)).toArgb()
    }

    private fun text(face: Typeface, size: Float, color: Color) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        this.color = color.toArgb()
    }

    companion object {
        const val WIDTH = 720
        const val HEIGHT = 1280
        const val FPS = 30
        private const val DENSITY = 2f
        private const val HEART_CENTER_Y = 600f
        private const val FLASH = 0.15f
        private const val FLASH_MS = 180L
        private const val END_CARD_MS = 2_000L

        /** Beats as a sorted array; the engine adds each beat's dub itself. */
        fun beats(beatsMs: List<Long>): LongArray = beatsMs.sorted().toLongArray()
    }
}
