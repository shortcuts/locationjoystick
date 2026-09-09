package com.locationjoystick.feature.settings.impl

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "QrShareDialog"
private val QR_IMAGE_SIZE = 220.dp

@Composable
fun QrShareDialog(
    qrText: String?,
    code: String?,
    isPreparing: Boolean,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val bitmap = remember(qrText) { qrText?.let { QrEncoder.encodeToQr(it) } }
    val ready = !isPreparing && qrText != null && code != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export via QR code") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!ready) {
                    Text(
                        "Starting local export server…",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(QR_IMAGE_SIZE),
                    ) {
                        CircularProgressIndicator()
                    }
                    return@Column
                }

                Text(
                    "Scan this on the other device — both must be on the same Wi-Fi network",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Export QR code",
                        modifier =
                            Modifier
                                .size(QR_IMAGE_SIZE)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    )
                } else {
                    Text("Failed to encode QR")
                }

                Text(
                    "Or enter code: $code",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        confirmButton = {
            LjButton(
                enabled = ready && bitmap != null,
                onClick = { if (bitmap != null) scope.launch { shareQrBitmap(context, bitmap) } },
            ) {
                Text("Share")
            }
        },
        dismissButton = {
            LjTextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
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
