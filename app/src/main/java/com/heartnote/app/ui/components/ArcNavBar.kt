package com.heartnote.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnDims
import com.heartnote.app.ui.theme.HnType

data class NavAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/**
 * DESIGN.md § ArcNavBar: five circles on a shallow upward arc. The centre
 * 64 dp black button sits highest with a 13 sp label under it; inner circles
 * sit [INNER_DROP] lower, outer circles a further 18 dp lower.
 */
@Composable
fun ArcNavBar(
    outerLeft: NavAction,
    innerLeft: NavAction,
    centre: NavAction,
    innerRight: NavAction,
    outerRight: NavAction,
    label: String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT),
    ) {
        val side = HnDims.circleButton
        val centreSize = HnDims.navCentreButton
        val centreY = centreSize / 2
        val innerY = centreY + INNER_DROP
        val outerY = innerY + OUTER_DROP
        val w = maxWidth

        @Composable
        fun circle(action: NavAction, fraction: Float, centerY: androidx.compose.ui.unit.Dp) {
            val cx = (w * fraction).coerceIn(side / 2, w - side / 2)
            CircleIconButton(
                icon = action.icon,
                contentDescription = action.contentDescription,
                onClick = action.onClick,
                enabled = action.enabled,
                modifier = Modifier.offset(x = cx - side / 2, y = centerY - side / 2),
            )
        }

        circle(outerLeft, OUTER_FRACTION, outerY)
        circle(innerLeft, INNER_FRACTION, innerY)
        circle(innerRight, 1f - INNER_FRACTION, innerY)
        circle(outerRight, 1f - OUTER_FRACTION, outerY)

        Column(
            Modifier
                .offset(x = w / 2 - LABEL_WIDTH / 2)
                .width(LABEL_WIDTH),
        ) {
            Box(Modifier.offset(x = LABEL_WIDTH / 2 - centreSize / 2)) {
                CircleIconButton(
                    icon = centre.icon,
                    contentDescription = centre.contentDescription,
                    onClick = centre.onClick,
                    enabled = centre.enabled,
                    size = centreSize,
                    containerColor = HnColors.InkBlack,
                    iconColor = HnColors.CardWhite,
                )
            }
            Text(
                text = label,
                style = HnType.navLabel,
                color = HnColors.InkBlack,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

private val BAR_HEIGHT = 104.dp
private val INNER_DROP = 18.dp
private val OUTER_DROP = 18.dp
private val LABEL_WIDTH = 120.dp
private const val OUTER_FRACTION = 0.085f
private const val INNER_FRACTION = 0.28f

@Preview(name = "ArcNavBar (Library)", widthDp = 360)
@Composable
private fun ArcNavBarPreview() {
    Box(Modifier.background(HnColors.LightBg).padding(vertical = 12.dp)) {
        ArcNavBar(
            outerLeft = NavAction(HeartnoteIcons.Shield, "Privacy", {}),
            innerLeft = NavAction(HeartnoteIcons.HandHeart, "Hold latest", {}),
            centre = NavAction(HeartnoteIcons.Pulse, "Record", {}),
            innerRight = NavAction(HeartnoteIcons.Share, "Share latest", {}),
            outerRight = NavAction(HeartnoteIcons.Info, "About", {}),
            label = "Record",
        )
    }
}
