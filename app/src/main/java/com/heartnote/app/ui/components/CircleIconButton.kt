package com.heartnote.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnDims

/**
 * DESIGN.md: 52 dp circle, 22 dp outlined icon. Light = CardWhite + InkBlack,
 * dark = DarkSurface + OnDark. [contentDescription] is required (accessibility).
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    enabled: Boolean = true,
    size: Dp = HnDims.circleButton,
    containerColor: Color = if (dark) HnColors.DarkSurface else HnColors.CardWhite,
    iconColor: Color = if (dark) HnColors.OnDark else HnColors.InkBlack,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = iconColor, modifier = Modifier.size(HnDims.circleIcon))
    }
}

@Preview(name = "CircleIconButton light + dark")
@Composable
private fun CircleIconButtonPreview() {
    Row(Modifier.background(HnColors.LightBg).padding(16.dp)) {
        CircleIconButton(HeartnoteIcons.Shield, "Privacy", {})
        CircleIconButton(HeartnoteIcons.History, "All notes", {}, Modifier.padding(start = 12.dp))
        Box(Modifier.padding(start = 12.dp).background(HnColors.DarkBg).padding(8.dp)) {
            CircleIconButton(HeartnoteIcons.Back, "Back", {}, dark = true)
        }
    }
}
