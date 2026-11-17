package com.heartnote.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Material defaults mapped onto Heartnote tokens, so stock components
 * (dialogs, sheets, text fields) match. Screens use HnColors/HnType directly.
 */
private val HeartnoteColorScheme = lightColorScheme(
    primary = HnColors.InkBlack,
    onPrimary = HnColors.CardWhite,
    secondary = HnColors.DotRed,
    onSecondary = HnColors.CardWhite,
    background = HnColors.LightBg,
    onBackground = HnColors.InkBlack,
    surface = HnColors.CardWhite,
    onSurface = HnColors.InkBlack,
    surfaceContainerLow = HnColors.CardWhite,
    surfaceContainer = HnColors.CardWhite,
    surfaceContainerHigh = HnColors.CardWhite,
    onSurfaceVariant = HnColors.InkGrey,
    error = HnColors.DotRed,
)

private val HeartnoteTypography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontFamily = Inter),
        bodyMedium = bodyMedium.copy(fontFamily = Inter),
        bodySmall = bodySmall.copy(fontFamily = Inter),
        titleLarge = titleLarge.copy(fontFamily = Inter),
        titleMedium = titleMedium.copy(fontFamily = Inter),
        labelLarge = labelLarge.copy(fontFamily = Inter),
        labelMedium = labelMedium.copy(fontFamily = Inter),
        headlineSmall = headlineSmall.copy(fontFamily = Inter),
    )
}

@Composable
fun HeartnoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HeartnoteColorScheme,
        typography = HeartnoteTypography,
        content = content,
    )
}
