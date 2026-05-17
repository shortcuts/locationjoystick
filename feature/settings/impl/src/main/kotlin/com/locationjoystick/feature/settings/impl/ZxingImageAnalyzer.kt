package com.locationjoystick.feature.settings.impl

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.json.JSONObject

class ZxingImageAnalyzer(
    private val onQrScanned: (ChunkEnvelope) -> Unit,
) : ImageAnalysis.Analyzer {
    private val multiFormatReader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }
    private var lastScanTime = 0L
    private var lastScannedData = ""

    override fun analyze(image: ImageProxy) {
        try {
            // Convert ImageProxy to bitmap for QR scanning
            val bitmap =
                when (image.format) {
                    ImageFormat.YUV_420_888 -> yuvToRgb(image)
                    else -> return // Unsupported format
                }

            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val rawResult = multiFormatReader.decodeWithState(binaryBitmap)
            val json = rawResult.text

            // Debounce: skip duplicate scans within 1 second
            val now = System.currentTimeMillis()
            if (json == lastScannedData && now - lastScanTime < 1000L) {
                return
            }

            val envelope = parseEnvelope(json)
            lastScannedData = json
            lastScanTime = now
            onQrScanned(envelope)
        } catch (e: NotFoundException) {
            // No QR found — silent, keep scanning
        } catch (e: Exception) {
            Log.w(TAG, "Scan error", e)
        } finally {
            image.close()
        }
    }

    private fun parseEnvelope(json: String): ChunkEnvelope {
        val obj = JSONObject(json)
        return ChunkEnvelope(
            k = obj.getString("k"),
            v = obj.getInt("v"),
            session = obj.getString("session"),
            chunk = obj.getInt("chunk"),
            total = obj.getInt("total"),
            d = obj.getString("d"),
        )
    }

    private fun yuvToRgb(image: ImageProxy): Bitmap {
        val planes = image.planes
        val ySize = planes[0].buffer.remaining()
        val uvSize = planes[1].buffer.remaining() + planes[2].buffer.remaining()

        val nv21 = ByteArray(ySize + uvSize)
        planes[0].buffer.get(nv21, 0, ySize)

        val uvPixelStride = planes[1].pixelStride
        if (uvPixelStride == 1) {
            val uSize = planes[1].buffer.remaining()
            planes[1].buffer.get(nv21, ySize, uSize)
            planes[2].buffer.get(nv21, ySize + uSize, planes[2].buffer.remaining())
        } else {
            val uvBuffer = ByteArray(uvSize)
            planes[1].buffer.get(uvBuffer, 0, planes[1].buffer.remaining())
            planes[2].buffer.get(uvBuffer, planes[1].buffer.remaining(), planes[2].buffer.remaining())
            for (i in uvBuffer.indices step 2) {
                nv21[ySize + i] = uvBuffer[i + 1]
                nv21[ySize + i + 1] = uvBuffer[i]
            }
        }

        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(image.width * image.height)

        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val index = y * image.width + x
                val yVal = nv21[index].toInt() and 0xFF
                val vVal = (nv21[image.width * image.height + (y / 2) * image.width + (x / 2) * 2].toInt() and 0xFF) - 128
                val uVal = (nv21[image.width * image.height + (y / 2) * image.width + (x / 2) * 2 + 1].toInt() and 0xFF) - 128

                val r = ((yVal + 1.402f * vVal).toInt()).coerceIn(0, 255)
                val g = ((yVal - 0.344f * uVal - 0.714f * vVal).toInt()).coerceIn(0, 255)
                val b = ((yVal + 1.772f * uVal).toInt()).coerceIn(0, 255)

                pixels[index] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        bitmap.setPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        return bitmap
    }

    private companion object {
        const val TAG = "ZxingImageAnalyzer"
    }
}
