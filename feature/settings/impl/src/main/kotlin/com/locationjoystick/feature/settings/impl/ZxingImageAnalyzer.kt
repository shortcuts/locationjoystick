package com.locationjoystick.feature.settings.impl

import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * CameraX ImageAnalysis analyzer that decodes QR codes using ZXing.
 *
 * Used by [QrScannerScreen] to scan QR codes emitted by [QrShareDialog].
 *
 * Key features:
 * - Debouncing: ignores duplicate scans within 500ms
 * - YUV format handling: converts camera YUV_420_888 to ZXing's planar format
 * - Error tolerance: logs failures but doesn't crash on bad codes
 *
 * @param onQrScanned Callback invoked with the raw decoded QR text
 */
class ZxingImageAnalyzer(
    private val onQrScanned: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val multiFormatReader =
        MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                ),
            )
        }
    private var lastScanTime = 0L
    private var lastScannedData = ""

    override fun analyze(image: ImageProxy) {
        try {
            if (image.format != ImageFormat.YUV_420_888) return

            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val yStride = yPlane.rowStride
            // PlanarYUVLuminanceSource requires dataWidth * dataHeight bytes.
            // yBuffer.remaining() is typically rowStride*(height-1)+width (last row has no padding),
            // so allocate the full rowStride*height and fill what the buffer provides.
            val yBytes = ByteArray(yStride * image.height)
            yBuffer.get(yBytes, 0, minOf(yBytes.size, yBuffer.remaining()))

            // CameraX ImageAnalysis does not rotate the buffer itself (unlike Preview) — it only
            // reports how far it's off via rotationDegrees. Rotate here or QR codes held upright
            // relative to what the user sees on screen decode at the wrong angle and never match.
            val rotationDegrees = image.imageInfo.rotationDegrees
            val (rotatedBytes, rotatedStride, rotatedWidth, rotatedHeight) =
                rotateYPlane(yBytes, yStride, image.width, image.height, rotationDegrees)

            val source =
                PlanarYUVLuminanceSource(
                    rotatedBytes,
                    rotatedStride,
                    rotatedHeight,
                    0,
                    0,
                    rotatedWidth,
                    rotatedHeight,
                    false,
                )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val rawResult = multiFormatReader.decodeWithState(binaryBitmap)
            val text = rawResult.text
            Log.d(TAG, "QR decoded: ${text.take(80)}")

            // Debounce: skip duplicate scans within 1 second
            val now = System.currentTimeMillis()
            if (text == lastScannedData && now - lastScanTime < 1000L) {
                return
            }

            lastScannedData = text
            lastScanTime = now
            onQrScanned(text)
        } catch (e: NotFoundException) {
            // No QR found — silent, keep scanning
        } catch (e: Exception) {
            Log.w(TAG, "Scan error", e)
        } finally {
            image.close()
        }
    }

    private data class RotatedFrame(
        val bytes: ByteArray,
        val stride: Int,
        val width: Int,
        val height: Int,
    )

    // Strips row padding (stride -> tight width) and rotates by rotationDegrees (0/90/180/270),
    // matching what the camera actually sees to what the user sees on screen.
    private fun rotateYPlane(
        src: ByteArray,
        srcStride: Int,
        width: Int,
        height: Int,
        rotationDegrees: Int,
    ): RotatedFrame {
        val tight = ByteArray(width * height)
        for (y in 0 until height) {
            System.arraycopy(src, y * srcStride, tight, y * width, width)
        }
        return when (rotationDegrees) {
            90 -> {
                val out = ByteArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        out[x * height + (height - y - 1)] = tight[y * width + x]
                    }
                }
                RotatedFrame(out, height, height, width)
            }

            180 -> {
                val out = ByteArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        out[width * height - y * width - x - 1] = tight[y * width + x]
                    }
                }
                RotatedFrame(out, width, width, height)
            }

            270 -> {
                val out = ByteArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        out[(width - x - 1) * height + y] = tight[y * width + x]
                    }
                }
                RotatedFrame(out, height, height, width)
            }

            else -> RotatedFrame(tight, width, width, height)
        }
    }

    private companion object {
        const val TAG = "ZxingImageAnalyzer"
    }
}
