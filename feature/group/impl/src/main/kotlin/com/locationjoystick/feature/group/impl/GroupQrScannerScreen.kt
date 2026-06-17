package com.locationjoystick.feature.group.impl

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.locationjoystick.core.designsystem.LjIcons
import java.util.concurrent.Executors

private const val TAG = "GroupQrScannerScreen"

@Composable
fun GroupQrScannerScreen(
    onQrScanned: (String) -> Unit,
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

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraPermissionGranted = granted
        }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraPermissionGranted) {
            val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
            val executor = remember { Executors.newSingleThreadExecutor() }

            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    cameraProviderFuture.addListener(
                        {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview =
                                Preview
                                    .Builder()
                                    .setResolutionSelector(
                                        ResolutionSelector
                                            .Builder()
                                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                                            .build(),
                                    ).build()
                                    .also { it.surfaceProvider = previewView.surfaceProvider }

                            val analyzer =
                                ImageAnalysis
                                    .Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { analysis ->
                                        analysis.setAnalyzer(
                                            executor,
                                            GroupRawQrAnalyzer { raw ->
                                                onQrScanned(raw)
                                            },
                                        )
                                    }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analyzer,
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Camera bind failed", e)
                            }
                        },
                        ContextCompat.getMainExecutor(ctx),
                    )
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Point at the leader's group QR code",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Text(
                text = "Camera permission required to scan QR code.",
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }

        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(LjIcons.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}

private class GroupRawQrAnalyzer(
    private val onResult: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val reader =
        MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                ),
            )
        }
    private var lastResult = ""
    private var lastTime = 0L

    override fun analyze(image: ImageProxy) {
        try {
            if (image.format != ImageFormat.YUV_420_888) return
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val yStride = yPlane.rowStride
            val yBytes = ByteArray(yStride * image.height)
            yBuffer.get(yBytes, 0, minOf(yBytes.size, yBuffer.remaining()))
            val source =
                PlanarYUVLuminanceSource(yBytes, yStride, image.height, 0, 0, image.width, image.height, false)
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            val raw = result.text
            val now = System.currentTimeMillis()
            if (raw != lastResult || now - lastTime > 2000L) {
                lastResult = raw
                lastTime = now
                onResult(raw)
            }
        } catch (_: NotFoundException) {
        } catch (e: Exception) {
            Log.w(TAG, "Scan error", e)
        } finally {
            image.close()
        }
    }
}
