package com.heartnote.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heartnote.app.R

/** DESIGN.md § DESIGN TOKENS — the only source of colours, type and sizes for screens. */
object HnColors {
    val LightBg = Color(0xFFF0F0F0)
    val CardWhite = Color(0xFFFFFFFF)
    val CardTint = Color(0xFFF8F8F8)
    val InkBlack = Color(0xFF1C1C1C)
    val InkGrey = Color(0xFF888888)
    val InkGreyLight = Color(0xFFA0A0A0)
    val DotIdle = Color(0xFFDADADA)
    val DotRed = Color(0xFFD8121A)
    val SignalRed = Color(0xFFFF0606)
    val DarkBg = Color(0xFF000000)
    val DarkSurface = Color(0xFF181818)
    val DarkTrack = Color(0xFF282828)
    val OnDark = Color(0xFFF8F8F8)
    val HoldGlow = Color(0xFFF72102)
    val HoldDeep = Color(0xFF300000)
    val HeartRed = Color(0xFFE2021B)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

object HnType {
    private val base = TextStyle(fontFamily = Inter)

    /** Two-tone heading line (colour applied per line). */
    val heading = base.copy(fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp, fontWeight = FontWeight.SemiBold)
    val screenTitle = base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val cardTitle = base.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val cardSubtitle = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, color = HnColors.InkGreyLight)
    val bodyPrimary = base.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val bodySecondary = base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val caption = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val axis = base.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal, color = HnColors.InkGreyLight)
    val navLabel = base.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val recordStatus = base.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
    val recordStatusSecondary = base.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal)
    val progressLabel = base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val disclaimer = base.copy(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Normal)
    val holdTitle = base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
}

object HnDims {
    val screenPadding = 20.dp
    val cardRadius = 28.dp
    val cardPadding = 18.dp
    val rowRadius = 20.dp
    val circleButton = 52.dp
    val circleIcon = 22.dp
    val navCentreButton = 64.dp
    val minTouch = 48.dp

    val cardShape = RoundedCornerShape(cardRadius)
    val rowShape = RoundedCornerShape(rowRadius)
    val pill = RoundedCornerShape(50)
}
