package com.heartnote.app.ui.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Hosts the CameraX [PreviewView] the SmartSpectra SDK renders into. The
 * surface provider must reach SignalCapture before sdk.start(). With
 * [desaturate] the view draws through a saturation-0 hardware layer
 * (DESIGN.md: grey preview inside the sphere core). Frames are only shown,
 * never stored (C3).
 */
@Composable
fun CameraPreview(
    onSurfaceProviderReady: (Preview.SurfaceProvider) -> Unit,
    modifier: Modifier = Modifier,
    desaturate: Boolean = false,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).apply {
                // TextureView-backed mode composites correctly inside Compose (alpha, clip).
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                onSurfaceProviderReady(surfaceProvider)
            }
        },
        update = { view ->
            if (desaturate) {
                val grey = Paint().apply {
                    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                }
                view.setLayerType(View.LAYER_TYPE_HARDWARE, grey)
            } else {
                view.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        },
    )
}
