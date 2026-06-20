package com.locationjoystick.feature.settings.impl

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/**
 * Encodes arbitrary text into a QR code bitmap using ZXing.
 *
 * Output is a 1024x1024 RGB-565 bitmap suitable for display in Compose.
 */
object QrEncoder {
    /**
     * Encodes [text] into a QR code bitmap.
     *
     * @return Bitmap ready for display, or null on failure
     */
    fun encodeToQr(text: String): Bitmap? =
        try {
            val writer = MultiFormatWriter()
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, BITMAP_SIZE_PX, BITMAP_SIZE_PX)
            createBitmapFromBitMatrix(bitMatrix)
        } catch (e: Exception) {
            Log.e(TAG, "QR encode failed", e)
            null
        }

    private fun createBitmapFromBitMatrix(bitMatrix: BitMatrix): Bitmap {
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private const val TAG = "QrEncoder"

    // Larger canvas = more pixels per module at any QR version, easier for a phone camera to resolve.
    private const val BITMAP_SIZE_PX = 1024
}
