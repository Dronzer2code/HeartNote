package com.heartnote.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.heartnote.app.ui.beat.BeatEvent
import com.heartnote.app.ui.beat.BeatKind
import com.heartnote.app.ui.components.particles.ParticleCanvas
import com.heartnote.app.ui.components.particles.ParticleEngine
import com.heartnote.app.ui.components.particles.ParticleModel
import com.heartnote.app.ui.components.particles.Ripples
import com.heartnote.app.ui.components.particles.RotationState
import com.heartnote.app.ui.theme.HnColors
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow

/**
 * DESIGN.md § PARTICLE SPHERE: 2,400 particles on a Fibonacci sphere,
 * r = R × (1 + 0.07·noise + 0.03·breath + beatImpulse + touchPush),
 * R = 0.36 × size, 6°/s idle spin, drag + fling, dark core over the particles.
 * [breath] returns the latest normalised brUpperTrace value (−1..1).
 */
@Composable
fun ParticleSphere(
    beatEvents: Flow<BeatEvent>?,
    breath: () -> Float,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val currentBreath = rememberUpdatedState(breath)
    val model = remember { SphereModel { currentBreath.value() } }
    ParticleCanvas(
        model = model,
        color = HnColors.SignalRed,
        beatEvents = beatEvents,
        modifier = modifier,
        interactive = interactive,
        fpsTag = "Sphere",
        overlay = { _ ->
            // Dark core so the digits stay readable (reference has a dark centre).
            val r = SphereModel.RADIUS_FRACTION * min(size.width, size.height) * CORE_FRACTION
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(HnColors.DarkBg.copy(alpha = 0.9f), Color.Transparent),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = r,
                ),
                radius = r,
            )
        },
    )
}

private const val CORE_FRACTION = 0.75f

internal class SphereModel(private val breathSource: () -> Float) : ParticleModel {
    override val points: FloatArray = fibonacciSphere(COUNT)
    override val unitFraction: Float = RADIUS_FRACTION

    // Precomputed per-particle phases of the three noise sinusoids.
    private val phase1 = FloatArray(COUNT)
    private val phase2 = FloatArray(COUNT)
    private val phase3 = FloatArray(COUNT)
    private var breath = 0f

    init {
        for (i in 0 until COUNT) {
            val x = points[i * 3]
            val y = points[i * 3 + 1]
            val z = points[i * 3 + 2]
            phase1[i] = 1.7f * (0.62f * x + 0.48f * y + 0.62f * z) * PI_F
            phase2[i] = 2.9f * (-0.71f * x + 0.39f * y + 0.59f * z) * PI_F
            phase3[i] = 4.3f * (0.27f * x - 0.86f * y + 0.43f * z) * PI_F
        }
    }

    override fun beginFrame(r: RotationState, tSec: Float, dtSec: Float, reducedMotion: Boolean) {
        // 300 ms low-pass on breath.
        val target = breathSource().coerceIn(-1f, 1f)
        breath += (target - breath) * (1f - exp(-dtSec / BREATH_TAU))

        if (!r.dragging) {
            r.yaw += r.velYaw * dtSec
            r.pitch += r.velPitch * dtSec
            val decay = ParticleEngine.flingDecay(dtSec)
            r.velYaw *= decay
            r.velPitch *= decay
            if (!reducedMotion) r.yaw += IDLE_SPIN_RAD_PER_S * dtSec
        }
        r.viewYaw = r.yaw
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
        if (reducedMotion) return 1f + BREATH_AMP * breath
        val noise = (sin(phase1[i] + 0.35f * tSec) + sin(phase2[i] + 0.55f * tSec) + sin(phase3[i] + 0.8f * tSec)) / 3f
        // θ from the camera-facing pole; the ripple reaches the far side 0.25 s later.
        val theta = acos(vz.coerceIn(-1f, 1f))
        val impulse = ripples.sum(theta / PI_F * RIPPLE_TRAVEL_S, tSec, RIPPLE_TAU_S)
        return 1f + NOISE_AMP * noise + BREATH_AMP * breath + impulse
    }

    override fun globalScale(tSec: Float, ripples: Ripples, reducedMotion: Boolean): Float {
        if (!reducedMotion) return 1f
        // Reduced motion: beats as one uniform 1.00 → 1.04 scale.
        return 1f + REDUCED_PULSE * ripples.sum(0f, tSec, RIPPLE_TAU_S) / LUB_AMP
    }

    override fun onBeat(event: BeatEvent, tSec: Float, ripples: Ripples) {
        ripples.add(tSec, if (event.kind == BeatKind.LUB) LUB_AMP else DUB_AMP, event.kind)
    }

    companion object {
        const val COUNT = 2_400
        const val RADIUS_FRACTION = 0.36f
        private const val PI_F = PI.toFloat()
        private const val NOISE_AMP = 0.07f
        private const val BREATH_AMP = 0.03f
        private const val BREATH_TAU = 0.3f
        private const val LUB_AMP = 0.13f
        private const val DUB_AMP = 0.065f
        private const val RIPPLE_TRAVEL_S = 0.25f
        private const val RIPPLE_TAU_S = 0.12f
        private const val REDUCED_PULSE = 0.04f
        private val IDLE_SPIN_RAD_PER_S = Math.toRadians(6.0).toFloat()

        /**
         * Fibonacci sphere with a tiny fixed angular jitter, which breaks the
         * lattice's visible spiral moiré without changing the distribution.
         */
        fun fibonacciSphere(n: Int, jitter: Float = 0.02f, seed: Int = 11): FloatArray {
            val out = FloatArray(n * 3)
            val golden = (PI * (3.0 - sqrt(5.0))).toFloat()
            val rnd = Random(seed)
            for (i in 0 until n) {
                val y = 1f - 2f * (i + 0.5f) / n
                val radius = sqrt(1f - y * y)
                val a = golden * i
                var x = cos(a) * radius + (rnd.nextFloat() - 0.5f) * jitter
                var yy = y + (rnd.nextFloat() - 0.5f) * jitter
                var z = sin(a) * radius + (rnd.nextFloat() - 0.5f) * jitter
                val len = sqrt(x * x + yy * yy + z * z)
                x /= len
                yy /= len
                z /= len
                out[i * 3] = x
                out[i * 3 + 1] = yy
                out[i * 3 + 2] = z
            }
            return out
        }
    }
}

@Preview(name = "ParticleSphere")
@Composable
private fun ParticleSpherePreview() {
    Box(Modifier.background(HnColors.DarkBg)) {
        ParticleSphere(beatEvents = null, breath = { 0f }, modifier = Modifier.size(320.dp))
    }
}
