package com.heartnote.app.ui.components

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Hosts the CameraX [PreviewView] the SmartSpectra SDK renders into.
 * The surface provider must be handed to [com.heartnote.app.capture.SignalCapture]
 * before sdk.start().
 */
@Composable
fun CameraPreview(
    onSurfaceProviderReady: (Preview.SurfaceProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).apply {
                // TextureView-backed mode composites correctly inside Compose layouts
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                onSurfaceProviderReady(surfaceProvider)
            }
        },
    )
}
