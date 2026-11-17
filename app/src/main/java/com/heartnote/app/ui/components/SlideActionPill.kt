package com.heartnote.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnDims
import com.heartnote.app.ui.theme.HnType
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

enum class PillAction { START, STOP }

/**
 * DESIGN.md § SlideActionPill: 180×64 dp DarkSurface pill, 52 dp SignalRed
 * knob (mic = start, rounded square = stop), centred label, three chevrons
 * with a left-to-right shimmer every 1.6 s. Tap, or swipe the knob right,
 * triggers [onAction].
 */
@Composable
fun SlideActionPill(
    action: PillAction,
    label: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val travelPx = with(density) { (PILL_WIDTH - KNOB - INSET * 2).toPx() }
    val knobX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val shimmer by rememberInfiniteTransition(label = "chevrons").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer",
    )

    Box(
        modifier = modifier
            .size(PILL_WIDTH, PILL_HEIGHT)
            .clip(HnDims.pill)
            .background(HnColors.DarkSurface)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onAction)
            .draggable(
                enabled = enabled,
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch { knobX.snapTo((knobX.value + delta).coerceIn(0f, travelPx)) }
                },
                onDragStopped = {
                    if (knobX.value > travelPx * TRIGGER_FRACTION) onAction()
                    knobX.animateTo(0f)
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .padding(start = INSET + KNOB + 16.dp, end = 18.dp)
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = HnType.progressLabel, color = HnColors.OnDark, modifier = Modifier.weight(1f))
            Row {
                for (i in 0 until 3) {
                    val phase = (shimmer * 3f - i).let { if (it in 0f..1f) 1f - it else 0f }
                    Icon(
                        HeartnoteIcons.ChevronRight,
                        contentDescription = null,
                        tint = HnColors.InkGrey,
                        modifier = Modifier
                            .size(14.dp)
                            .offset(x = (-6 * i).dp)
                            .alpha(if (enabled) 0.35f + 0.65f * phase else 0.35f),
                    )
                }
            }
        }
        Box(
            Modifier
                .padding(start = INSET)
                .offset { IntOffset(knobX.value.roundToInt(), 0) }
                .size(KNOB)
                .clip(CircleShape)
                .background(HnColors.SignalRed),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (action == PillAction.START) HeartnoteIcons.Mic else HeartnoteIcons.StopSquare,
                contentDescription = null,
                tint = HnColors.CardWhite,
                modifier = Modifier.size(if (action == PillAction.START) 22.dp else 18.dp),
            )
        }
    }
}

private val PILL_WIDTH = 180.dp
private val PILL_HEIGHT = 64.dp
private val KNOB = 52.dp
private val INSET = 6.dp
private const val TRIGGER_FRACTION = 0.7f
private const val SHIMMER_MS = 1600

@Preview(name = "SlideActionPill Start / Stop / disabled")
@Composable
private fun SlideActionPillPreview() {
    Column(Modifier.background(HnColors.DarkBg).padding(16.dp)) {
        SlideActionPill(PillAction.START, "Start", {})
        SlideActionPill(PillAction.STOP, "Stop", {}, Modifier.padding(top = 12.dp))
        SlideActionPill(PillAction.STOP, "Stop", {}, Modifier.padding(top = 12.dp), enabled = false)
    }
}
