package com.heartnote.app.export

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Design check of exported frames (the encoder draws exactly this on its Surface). */
@Composable
private fun FramePreview(timeMs: Long) {
    val context = LocalContext.current
    val frame = remember {
        HeartnoteVideoFrame(context, "Mum", (300L..42_000L step 760L).toList().toLongArray(), 42_000)
    }
    Canvas(Modifier.size(360.dp, 640.dp)) {
        drawIntoCanvas { c ->
            c.nativeCanvas.save()
            c.nativeCanvas.scale(size.width / HeartnoteVideoFrame.WIDTH, size.height / HeartnoteVideoFrame.HEIGHT)
            frame.draw(c.nativeCanvas, timeMs)
            c.nativeCanvas.restore()
        }
    }
}

@Preview(name = "Video frame — on a lub", widthDp = 360, heightDp = 640)
@Composable
private fun VideoFrameLubPreview() = FramePreview(12_210)

@Preview(name = "Video frame — end card", widthDp = 360, heightDp = 640)
@Composable
private fun VideoFrameEndPreview() = FramePreview(41_200)
