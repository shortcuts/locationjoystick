package com.locationjoystick.feature.settings.impl

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "QrShareDialog"

@Composable
fun QrShareDialog(
    qrText: String,
    code: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val bitmap = remember(qrText) { QrEncoder.encodeToQr(qrText) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(0.9f)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(16.dp),
        ) {
            Text(
                "Scan this on the other device — both must be on the same Wi-Fi network",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Export QR code",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            } else {
                Text("Failed to encode QR")
            }

            Text(
                "Or enter code: $code",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
            )

            Button(
                onClick = {
                    if (bitmap != null) {
                        scope.launch { shareQrBitmap(context, bitmap) }
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp),
            ) {
                Text("Share")
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
            ) {
                Text("Done")
            }
        }
    }
}

private suspend fun shareQrBitmap(
    context: Context,
    bitmap: Bitmap,
) {
    withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "qr_share").also { it.mkdirs() }
            val file = File(cacheDir, "qr_export.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, "Share QR Code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Share QR bitmap failed", e)
        }
    }
}
