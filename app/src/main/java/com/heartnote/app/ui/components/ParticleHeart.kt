package com.heartnote.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.heartnote.app.ui.beat.BeatEvent
import com.heartnote.app.ui.beat.BeatKind
import com.heartnote.app.ui.components.particles.ParticleCanvas
import com.heartnote.app.ui.components.particles.ParticleModel
import com.heartnote.app.ui.components.particles.Ripples
import com.heartnote.app.ui.components.particles.RotationState
import com.heartnote.app.ui.theme.HnColors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * DESIGN.md § HOLD SCREEN HEART: a particle heart on the shared ParticleEngine.
 * The canvas should be a square as wide as the screen; the heart's height is
 * then 0.70 × that width. Points are generated once off the main thread.
 */
@Composable
fun ParticleHeart(beatEvents: Flow<BeatEvent>?, modifier: Modifier = Modifier) {
    val points by produceState<HeartPoints?>(HeartPoints.cached) {
        if (value == null) value = withContext(Dispatchers.Default) { HeartPoints.get() }
    }
    Box(modifier) {
        val p = points ?: return@Box
        ParticleCanvas(
            model = androidx.compose.runtime.remember(p) { HeartModel(p) },
            color = HnColors.HeartRed,
            beatEvents = beatEvents,
            modifier = Modifier.matchParentSize(),
            fpsTag = "Heart",
        )
    }
}

/** Heart surface points in engine coordinates (X right, Y up, Z toward the viewer). */
class HeartPoints private constructor(val xyz: FloatArray, val heightUnits: Float) {
    companion object {
        private const val COUNT = 2_600
        private const val SHELL = 1.04f

        @Volatile
        var cached: HeartPoints? = null
            private set

        @Synchronized
        fun get(): HeartPoints = cached ?: generate().also { cached = it }

        /**
         * F(x, y, z) = (x² + 9/4·y² + z² − 1)³ − x²·z³ − 9/80·y²·z³ with z
         * vertical and y depth. Keep p with F(p) ≤ 0 and F(1.04·p) > 0.
         */
        private fun f(x: Float, y: Float, z: Float): Float {
            val a = x * x + 2.25f * y * y + z * z - 1f
            val z3 = z * z * z
            return a * a * a - x * x * z3 - 0.1125f * y * y * z3
        }

        fun generate(seed: Int = 5): HeartPoints {
            val rnd = Random(seed)
            val raw = FloatArray(COUNT * 3)
            var n = 0
            while (n < COUNT) {
                val x = rnd.nextFloat() * 3f - 1.5f
                val y = rnd.nextFloat() * 2f - 1f
                val z = rnd.nextFloat() * 3f - 1.5f
                if (f(x, y, z) <= 0f && f(SHELL * x, SHELL * y, SHELL * z) > 0f) {
                    // Engine axes: X = x, Y (up) = z, Z (depth) = y.
                    raw[n * 3] = x
                    raw[n * 3 + 1] = z
                    raw[n * 3 + 2] = y
                    n++
                }
            }
            // Recentre on the bounding box and scale the largest dimension to 2.0.
            val min = FloatArray(3) { Float.MAX_VALUE }
            val maxV = FloatArray(3) { -Float.MAX_VALUE }
            for (i in 0 until COUNT) for (a in 0 until 3) {
                min[a] = minOf(min[a], raw[i * 3 + a])
                maxV[a] = max(maxV[a], raw[i * 3 + a])
            }
            val largest = (0 until 3).maxOf { maxV[it] - min[it] }
            val scale = 2f / largest
            for (i in 0 until COUNT) for (a in 0 until 3) {
                raw[i * 3 + a] = (raw[i * 3 + a] - (min[a] + maxV[a]) / 2f) * scale
            }
            return HeartPoints(raw, (maxV[1] - min[1]) * scale)
        }
    }
}

internal class HeartModel(heart: HeartPoints) : ParticleModel {
    override val points: FloatArray = heart.xyz
    /** Height = 0.70 × the canvas width (a screen-wide square). */
    override val unitFraction: Float = HEIGHT_OF_WIDTH / heart.heightUnits

    private val count = points.size / 3
    private val rippleDelay = FloatArray(count)
    private val phase1 = FloatArray(count)
    private val phase2 = FloatArray(count)
    private val phase3 = FloatArray(count)

    init {
        var maxDist = 0f
        for (i in 0 until count) {
            val d = sqrt(points[i * 3].pow(2) + points[i * 3 + 1].pow(2) + points[i * 3 + 2].pow(2))
            rippleDelay[i] = d
            maxDist = max(maxDist, d)
        }
        for (i in 0 until count) {
            rippleDelay[i] = rippleDelay[i] / maxDist * RIPPLE_TRAVEL_S
            val x = points[i * 3]
            val y = points[i * 3 + 1]
            val z = points[i * 3 + 2]
            phase1[i] = 1.7f * (0.62f * x + 0.48f * y + 0.62f * z) * PI_F
            phase2[i] = 2.9f * (-0.71f * x + 0.39f * y + 0.59f * z) * PI_F
            phase3[i] = 4.3f * (0.27f * x - 0.86f * y + 0.43f * z) * PI_F
        }
    }

    override fun beginFrame(r: RotationState, tSec: Float, dtSec: Float, reducedMotion: Boolean) {
        if (!r.dragging) {
            // Spring back to the sway after release.
            val k = exp(-dtSec / SPRING_TAU)
            r.yaw *= k
            r.pitch *= k
            r.velYaw = 0f
            r.velPitch = 0f
        }
        r.yaw = r.yaw.coerceIn(-MAX_YAW, MAX_YAW)
        r.pitch = r.pitch.coerceIn(-MAX_PITCH, MAX_PITCH)
        val sway = if (reducedMotion) 0f else SWAY * sin(2f * PI_F * tSec / SWAY_PERIOD_S)
        r.viewYaw = (sway + r.yaw).coerceIn(-MAX_YAW, MAX_YAW)
        r.viewPitch = r.pitch
    }

    override fun radiusScale(
        i: Int,
        ox: Float, oy: Float, oz: Float,
        vx: Float, vy: Float, vz: Float,
        tSec: Float,
        ripples: Ripples,
        reducedMotion: Boolean,
    ): Float {
        if (reducedMotion) return 1f
        val noise = (sin(phase1[i] + 0.35f * tSec) + sin(phase2[i] + 0.55f * tSec) + sin(phase3[i] + 0.8f * tSec)) / 3f
        return 1f + NOISE_AMP * noise + ripples.sum(rippleDelay[i], tSec, RIPPLE_TAU_S)
    }

    override fun globalScale(tSec: Float, ripples: Ripples, reducedMotion: Boolean): Float =
        1f + pulse(ripples.sinceLast(BeatKind.LUB, tSec), LUB_SCALE, LUB_UP_S, LUB_DOWN_S) +
            pulse(ripples.sinceLast(BeatKind.DUB, tSec), DUB_SCALE, DUB_UP_S, DUB_DOWN_S)

    override fun onBeat(event: BeatEvent, tSec: Float, ripples: Ripples) {
        ripples.add(tSec, if (event.kind == BeatKind.LUB) LUB_RIPPLE else DUB_RIPPLE, event.kind)
    }

    /** 0 → peak (ease-out) over [up], back to 0 (ease-in) over [down]. */
    private fun pulse(age: Float, peak: Float, up: Float, down: Float): Float = when {
        age < 0f || age >= up + down -> 0f
        age < up -> peak * (1f - (1f - age / up).pow(2))
        else -> peak * (1f - ((age - up) / down).pow(2))
    }

    companion object {
        private const val PI_F = PI.toFloat()
        private const val HEIGHT_OF_WIDTH = 0.70f
        private const val NOISE_AMP = 0.03f
        private const val RIPPLE_TRAVEL_S = 0.20f
        private const val RIPPLE_TAU_S = 0.12f
        private const val LUB_RIPPLE = 0.06f
        private const val DUB_RIPPLE = 0.03f
        private const val LUB_SCALE = 0.07f
        private const val LUB_UP_S = 0.09f
        private const val LUB_DOWN_S = 0.22f
        private const val DUB_SCALE = 0.035f
        private const val DUB_UP_S = 0.07f
        private const val DUB_DOWN_S = 0.18f
        private val SWAY = Math.toRadians(25.0).toFloat()
        private const val SWAY_PERIOD_S = 6f
        private val MAX_YAW = Math.toRadians(60.0).toFloat()
        private val MAX_PITCH = Math.toRadians(30.0).toFloat()
        private const val SPRING_TAU = 0.35f
    }
}

@Preview(name = "ParticleHeart")
@Composable
private fun ParticleHeartPreview() {
    Box(Modifier.background(HnColors.DarkBg)) {
        ParticleHeart(beatEvents = null, modifier = Modifier.size(360.dp))
    }
}
