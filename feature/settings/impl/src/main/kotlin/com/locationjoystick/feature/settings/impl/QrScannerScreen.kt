package com.locationjoystick.feature.settings.impl

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.locationjoystick.core.designsystem.LjIcons
import java.util.concurrent.Executors
import androidx.compose.ui.tooling.preview.Preview as ComposePreview

/**
 * QR code scanner screen for importing settings via camera.
 *
 * Uses CameraX + ZXing to scan QR codes emitted by [QrShareDialog].
 * Scanned chunks are passed to the caller (typically [SettingsViewModel])
 * for reassembly via [ChunkReassembler].
 *
 * Flow:
 * 1. Request camera permission
 * 2. Start camera preview with [ZxingImageAnalyzer]
 * 3. On QR detection, parse [ChunkEnvelope] from JSON
 * 4. Pass valid chunks to [onChunkScanned]
 * 5. Caller tracks progress and reassembles when all chunks received
 *
 * Requires CAMERA permission (handled internally).
 *
 * @param onChunkScanned Called with each successfully scanned chunk
 * @param onPermissionDenied Called when user denies camera permission
 * @param onNavigateBack Called when user taps back button
 */
@ComposePreview(showBackground = true)
@Composable
private fun QrScannerScreenPreview() {
    QrScannerScreen(
        onChunkScanned = {},
        onPermissionDenied = {},
        onNavigateBack = {},
    )
}

@Composable
fun QrScannerScreen(
    onChunkScanned: (ChunkEnvelope) -> Unit,
    onPermissionDenied: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequestedOnce by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                cameraPermissionGranted = true
            } else {
                onPermissionDenied()
            }
        }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted && !permissionRequestedOnce) {
            permissionRequestedOnce = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!cameraPermissionGranted) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Camera permission required to scan QR codes")
            Button(onClick = onNavigateBack) {
                Text("Back to Settings")
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView =
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    try {
                        provider.unbindAll()

                        val preview =
                            Preview.Builder().build().apply {
                                setSurfaceProvider(previewView.surfaceProvider)
                            }

                        val imageAnalysis =
                            ImageAnalysis
                                .Builder()
                                .setResolutionSelector(
                                    ResolutionSelector
                                        .Builder()
                                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                                        .build(),
                                ).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .apply {
                                    setAnalyzer(
                                        Executors.newSingleThreadExecutor(),
                                        ZxingImageAnalyzer(onChunkScanned),
                                    )
                                }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                    } catch (e: Exception) {
                        Log.e("QrScannerScreen", "Bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
        ) {
            Text(
                "Point camera at QR code",
                modifier =
                    Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(8.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Icon(LjIcons.ArrowBack, contentDescription = "Back")
        }
    }
}
