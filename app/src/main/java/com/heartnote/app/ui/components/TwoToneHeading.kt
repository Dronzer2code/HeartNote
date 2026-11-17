package com.heartnote.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.heartnote.app.ui.theme.HnColors
import com.heartnote.app.ui.theme.HnType

/** DESIGN.md: 34 sp SemiBold, line 1 InkGrey, line 2 InkBlack (or [blackColor]). */
@Composable
fun TwoToneHeading(
    grey: String,
    black: String,
    modifier: Modifier = Modifier,
    blackColor: androidx.compose.ui.graphics.Color = HnColors.InkBlack,
) {
    Column(modifier.semantics(mergeDescendants = true) { heading() }) {
        Text(grey, style = HnType.heading, color = HnColors.InkGrey, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(black, style = HnType.heading, color = blackColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Preview(name = "TwoToneHeading", widthDp = 360)
@Composable
private fun TwoToneHeadingPreview() {
    Column(Modifier.background(HnColors.LightBg).padding(20.dp)) {
        TwoToneHeading("Send your", "heartbeat")
        TwoToneHeading("Heartnote for", "you", Modifier.padding(top = 24.dp))
    }
}
