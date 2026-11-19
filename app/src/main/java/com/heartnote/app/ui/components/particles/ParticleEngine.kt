package com.heartnote.app.ui.components.particles

import android.graphics.Paint
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heartnote.app.BuildConfig
import com.heartnote.app.ui.beat.BeatEvent
import com.heartnote.app.ui.beat.BeatKind
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive

/**
 * DESIGN.md § PARTICLE ENGINE — shared by ParticleSphere and ParticleHeart.
 * Coordinates: X right, Y up, Z toward the viewer, unit scale. The model
 * supplies points, per-particle radial scale and its rotation policy; the
 * engine does rotation state, drag/fling, touch push, beat ripples,
 * perspective projection, 4 depth buckets, glow, frame loop, FPS overlay and
 * reduced motion. No allocation per frame.
 */
interface ParticleModel {
    /** Unit-scale points, xyz interleaved. */
    val points: FloatArray

    /** Screen size of one model unit, as a fraction of the canvas' smaller side. */
    val unitFraction: Float

    /** Once per frame: update model state and write [RotationState.viewYaw]/[RotationState.viewPitch]. */
    fun beginFrame(r: RotationState, tSec: Float, dtSec: Float, reducedMotion: Boolean)

    /**
     * Radial scale (1 = rest) of particle [i]. (ox, oy, oz) = object space,
     * (vx, vy, vz) = after rotation (vz > 0 faces the viewer).
     */
    fun radiusScale(
        i: Int,
        ox: Float, oy: Float, oz: Float,
        vx: Float, vy: Float, vz: Float,
        tSec: Float,
        ripples: Ripples,
        reducedMotion: Boolean,
    ): Float

    /** Uniform scale of the whole shape this frame. */
    fun globalScale(tSec: Float, ripples: Ripples, reducedMotion: Boolean): Float = 1f

    /** Turn a beat into ripples (amplitude per kind is model-specific). */
    fun onBeat(event: BeatEvent, tSec: Float, ripples: Ripples)
}

class RotationState {
    /** Accumulated rotation from idle motion, drag and fling (radians). */
    var yaw = 0f
    var pitch = 0f
    var velYaw = 0f
    var velPitch = 0f
    var dragging = false

    /** Final rotation for this frame, written by the model. */
    var viewYaw = 0f
    var viewPitch = 0f
}

/** Active beat ripples, fixed capacity, oldest overwritten. */
class Ripples(private val capacity: Int = 16) {
    private val start = FloatArray(capacity)
    private val amp = FloatArray(capacity)
    private val kind = IntArray(capacity)
    private var next = 0
    private var size = 0

    fun add(tSec: Float, amplitude: Float, beatKind: BeatKind) {
        start[next] = tSec
        amp[next] = amplitude
        kind[next] = beatKind.ordinal
        next = (next + 1) % capacity
        if (size < capacity) size++
    }

    /** Σ A·exp(−(t − t0 − delay)/τ) over ripples that have reached this point. */
    fun sum(delaySec: Float, tSec: Float, tauSec: Float, maxAgeSec: Float = 1.5f): Float {
        var total = 0f
        for (k in 0 until size) {
            val age = tSec - start[k] - delaySec
            if (age >= 0f && age < maxAgeSec) total += amp[k] * exp(-age / tauSec)
        }
        return total
    }

    fun clear() {
        next = 0
        size = 0
    }

    /** Seconds since the newest ripple of [beatKind] started, or +inf. */
    fun sinceLast(beatKind: BeatKind, tSec: Float): Float {
        var best = Float.POSITIVE_INFINITY
        for (k in 0 until size) {
            if (kind[k] == beatKind.ordinal) best = min(best, tSec - start[k])
        }
        return best
    }
}

class ParticleEngine(private val model: ParticleModel) {
    val count = model.points.size / 3
    val rotation = RotationState()
    val ripples = Ripples()
    var timeSec = 0f
        private set

    private val buckets = Array(BUCKETS) { FloatArray(count * 2) }
    private val bucketCounts = IntArray(BUCKETS)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    var widthPx = 0f
    var heightPx = 0f
    private var touchX = 0f
    private var touchY = 0f
    private var touchDown = false
    private var touchStrength = 0f

    fun touch(x: Float, y: Float) {
        touchX = x
        touchY = y
        touchDown = true
    }

    fun release() {
        touchDown = false
    }

    fun beat(event: BeatEvent) = model.onBeat(event, timeSec, ripples)

    fun step(tSec: Float, dtSec: Float, touchRadiusPx: Float, reducedMotion: Boolean) {
        timeSec = tSec
        model.beginFrame(rotation, tSec, dtSec, reducedMotion)

        // Touch push follows the finger quickly and springs back over ~350 ms.
        touchStrength = if (touchDown) {
            touchStrength + (1f - touchStrength) * (1f - exp(-dtSec / 0.04f))
        } else {
            touchStrength * exp(-dtSec / TOUCH_RELEASE_TAU)
        }

        val cy0 = cos(rotation.viewYaw)
        val sy0 = sin(rotation.viewYaw)
        val cp = cos(rotation.viewPitch)
        val sp = sin(rotation.viewPitch)
        val gs = model.globalScale(tSec, ripples, reducedMotion)
        val unitPx = model.unitFraction * min(widthPx, heightPx)
        val cx = widthPx / 2
        val cyScreen = heightPx / 2
        val pts = model.points
        bucketCounts.fill(0)

        for (i in 0 until count) {
            val ox = pts[i * 3]
            val oy = pts[i * 3 + 1]
            val oz = pts[i * 3 + 2]
            // Yaw around Y, then pitch around X.
            val x1 = ox * cy0 + oz * sy0
            val z1 = -ox * sy0 + oz * cy0
            val vy = oy * cp - z1 * sp
            val vz = oy * sp + z1 * cp
            var s = model.radiusScale(i, ox, oy, oz, x1, vy, vz, tSec, ripples, reducedMotion) * gs

            var f = CAMERA_DISTANCE / (CAMERA_DISTANCE - vz * s)
            var sx = cx + x1 * s * unitPx * f
            var sy = cyScreen - vy * s * unitPx * f

            if (touchStrength > 0.001f) {
                val dx = sx - touchX
                val dy = sy - touchY
                val d = sqrt(dx * dx + dy * dy)
                if (d < touchRadiusPx) {
                    s += TOUCH_PUSH * (1f - d / touchRadiusPx) * touchStrength
                    f = CAMERA_DISTANCE / (CAMERA_DISTANCE - vz * s)
                    sx = cx + x1 * s * unitPx * f
                    sy = cyScreen - vy * s * unitPx * f
                }
            }

            val depth = ((vz * s / DEPTH_RANGE) + 1f) * 0.5f
            val b = (depth * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)
            val n = bucketCounts[b]
            buckets[b][n * 2] = sx
            buckets[b][n * 2 + 1] = sy
            bucketCounts[b] = n + 1
        }
    }

    /** Back-to-front, one drawPoints call per depth bucket. */
    fun draw(scope: DrawScope, color: Color, alpha: Float = 1f) {
        val density = scope.density
        scope.drawIntoCanvas { canvas -> drawTo(canvas.nativeCanvas, color.toArgb(), density, alpha) }
    }

    fun drawTo(native: android.graphics.Canvas, argb: Int, density: Float, alpha: Float = 1f) {
        for (b in 0 until BUCKETS) {
            val count = bucketCounts[b]
            if (count == 0) continue
            val t = b / (BUCKETS - 1f)
            paint.strokeWidth = (SIZE_BACK_DP + (SIZE_FRONT_DP - SIZE_BACK_DP) * t) * density
            paint.color = argb
            paint.alpha = ((ALPHA_BACK + (ALPHA_FRONT - ALPHA_BACK) * t) * alpha * 255).toInt()
            native.drawPoints(buckets[b], 0, count * 2, paint)
        }
    }

    /**
     * SHARE_AND_IMPORT.md § VIDEO EXPORT: deterministic frame at [timeMs].
     * Ripples and beat scale come from [beatsMs] relative to [timeMs] (not
     * BeatBus or the wall clock), so the same note always renders the same
     * frames. Live screens keep using [beat] + [step].
     */
    fun renderAt(
        timeMs: Long,
        beatsMs: LongArray,
        canvas: android.graphics.Canvas,
        argb: Int,
        density: Float,
        frameDtSec: Float = 1f / 30f,
    ) {
        prepareAt(timeMs, beatsMs, frameDtSec)
        drawTo(canvas, argb, density)
    }

    /** Deterministic projection for [timeMs] (the drawing-free half of [renderAt]). */
    fun prepareAt(timeMs: Long, beatsMs: LongArray, frameDtSec: Float = 1f / 30f) {
        val tSec = timeMs / 1000f
        ripples.clear()
        val from = timeMs - RIPPLE_WINDOW_MS
        for (i in beatsMs.indices) {
            val b = beatsMs[i]
            if (b > timeMs) break
            if (b < from) continue
            val rr = if (i + 1 < beatsMs.size) beatsMs[i + 1] - b else if (i > 0) b - beatsMs[i - 1] else 800L
            model.onBeat(BeatEvent(BeatKind.LUB, 1f, b), b / 1000f, ripples)
            val dub = b + com.heartnote.app.audio.HeartbeatSynth.dubDelayMs(rr)
            if (dub <= timeMs) model.onBeat(BeatEvent(BeatKind.DUB, 0.5f, dub), dub / 1000f, ripples)
        }
        step(tSec, frameDtSec, 0f, reducedMotion = false)
    }

    /** Projected positions of every bucket, for determinism tests. */
    internal fun snapshot(): FloatArray {
        val out = ArrayList<Float>()
        for (b in 0 until BUCKETS) for (i in 0 until bucketCounts[b] * 2) out += buckets[b][i]
        return out.toFloatArray()
    }

    companion object {
        const val BUCKETS = 4
        private const val RIPPLE_WINDOW_MS = 1_500L
        const val CAMERA_DISTANCE = 3f       // × R (DESIGN.md: camera distance 3 R)
        private const val DEPTH_RANGE = 1.15f
        // DESIGN.md gives 1.2–2.8 "px"; applied as dp so dots stay visible on 3× screens.
        private const val SIZE_BACK_DP = 1.2f
        private const val SIZE_FRONT_DP = 2.8f
        private const val ALPHA_BACK = 0.2f
        private const val ALPHA_FRONT = 1.0f
        private const val TOUCH_PUSH = 0.12f
        private const val TOUCH_RELEASE_TAU = 0.1f // ≈ 350 ms to settle
        const val DEG_PER_DP = 1f                  // drag: 1° per dp
        const val FLING_DECAY_PER_FRAME = 0.92f

        /** Fling velocity decay for a frame of [dtSec] (0.92 per 60 Hz frame). */
        fun flingDecay(dtSec: Float): Float = FLING_DECAY_PER_FRAME.pow(dtSec * 60f)
    }
}

fun isReducedMotion(context: android.content.Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * Hosts a [ParticleEngine]: frame loop (withFrameNanos), beat subscription,
 * drag rotation with fling, touch push, glow layer (API 31+), [overlay]
 * drawn above the particles, and a debug-only FPS overlay.
 */
@Composable
fun ParticleCanvas(
    model: ParticleModel,
    color: Color,
    beatEvents: Flow<BeatEvent>?,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    fpsTag: String = "Particles",
    /** 0 = every display frame. RecordScreen caps at 30 so the SDK keeps the CPU/GPU it needs. */
    maxFps: Int = 0,
    /** Blur glow layer (API 31+). Off while recording: a full-layer blur every frame is costly. */
    glow: Boolean = true,
    overlay: (DrawScope.(engine: ParticleEngine) -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val engine = remember(model) { ParticleEngine(model) }
    val reducedMotion = remember { isReducedMotion(context) }
    val touchRadiusPx = with(density) { TOUCH_RADIUS.toPx() }
    val radPerPx = remember(density) { Math.toRadians(ParticleEngine.DEG_PER_DP.toDouble()).toFloat() / density.density }
    var frameNanos by remember { mutableLongStateOf(0L) }
    var fps by remember { mutableIntStateOf(0) }

    LaunchedEffect(engine, beatEvents) {
        beatEvents?.collect { engine.beat(it) }
    }

    LaunchedEffect(engine) {
        var start = -1L
        var last = 0L
        var windowStart = 0L
        var frames = 0
        var lastLog = 0L
        val minFrameNanos = if (maxFps > 0) 1_000_000_000L / maxFps - 2_000_000L else 0L
        while (isActive) {
            withFrameNanos { now ->
                if (start < 0) {
                    start = now
                    last = now
                    windowStart = now
                }
                // Frame cap: skip this vsync (no step, no redraw) until the next slot.
                if (minFrameNanos > 0 && now != start && now - last < minFrameNanos) return@withFrameNanos
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                engine.step((now - start) / 1e9f, dt, touchRadiusPx, reducedMotion)
                frameNanos = now
                if (BuildConfig.DEBUG) {
                    frames++
                    if (now - windowStart >= 1_000_000_000L) {
                        fps = (frames * 1e9 / (now - windowStart)).toInt()
                        frames = 0
                        windowStart = now
                        if (now - lastLog >= 5_000_000_000L) {
                            Log.d(FPS_LOG_TAG, "$fpsTag fps=$fps")
                            lastLog = now
                        }
                    }
                }
            }
        }
    }

    val gestures = if (!interactive) {
        Modifier
    } else {
        Modifier.pointerInput(engine) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                engine.touch(down.position.x, down.position.y)
                engine.rotation.dragging = true
                engine.rotation.velYaw = 0f
                engine.rotation.velPitch = 0f
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val delta = change.positionChange()
                    engine.rotation.yaw += delta.x * radPerPx
                    engine.rotation.pitch += delta.y * radPerPx
                    engine.touch(change.position.x, change.position.y)
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (delta.x != 0f || delta.y != 0f) change.consume()
                }
                val v = tracker.calculateVelocity()
                engine.rotation.velYaw = v.x * radPerPx
                engine.rotation.velPitch = v.y * radPerPx
                engine.rotation.dragging = false
                engine.release()
            }
        }
    }

    Box(
        modifier
            .onSizeChanged {
                engine.widthPx = it.width.toFloat()
                engine.heightPx = it.height.toFloat()
                // First frame immediately (also makes static @Previews show the shape).
                engine.step(engine.timeSec, 0f, touchRadiusPx, reducedMotion)
            }
            .then(gestures)
            .clearAndSetSemantics { },
    ) {
        if (glow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurPx = with(density) { GLOW_BLUR.toPx() }
            Canvas(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        renderEffect = BlurEffect(blurPx, blurPx, TileMode.Decal)
                        alpha = GLOW_ALPHA
                    },
            ) {
                if (frameNanos >= 0) engine.draw(this, color)
            }
        }
        Canvas(Modifier.matchParentSize()) {
            if (frameNanos >= 0) engine.draw(this, color)
            overlay?.invoke(this, engine)
        }
        if (BuildConfig.DEBUG) {
            Text(
                "$fps fps",
                color = Color(0xFF00FF66),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            )
        }
    }
}

const val FPS_LOG_TAG = "HeartnoteFps"
private val TOUCH_RADIUS = 56.dp
private val GLOW_BLUR = 14.dp
private const val GLOW_ALPHA = 0.35f
