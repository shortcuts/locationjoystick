# QR Share / Transfer

Settings → share or import config between devices over the local network, kicked off by scanning a single QR code. Both devices must be on the same Wi-Fi/LAN.

Key files: `:feature:settings:impl/ExportSyncServer.kt`, `:feature:settings:impl/ExportSyncClient.kt`, `:feature:settings:impl/QrScannerScreen.kt`, `:feature:settings:impl/QrShareDialog.kt`, `:feature:settings:impl/QrEncoder.kt`, `:core:common/util/NetworkUtils.kt`

## Mechanism

The QR code does not carry the export data itself — it carries connection info for a transient local HTTP server. This avoids any size limit, multi-QR chunking, or scan-sequence logic.

1. **Sender** taps "Export via QR code". `SettingsViewModel.prepareQrExport()`:
   - Serializes current settings via `SettingsExportCodec.serializeExportData`.
   - Generates a random one-shot token.
   - Starts `ExportSyncServer` (a `ServerSocket(0)`-bound local HTTP server, OS-assigned port), which serves that JSON at `GET /export?token=TOKEN`.
   - Builds `locationjoystick://export?host=HOST&port=PORT&token=TOKEN` (HOST resolved via `NetworkUtils.getLocalIpAddress()`) and renders it as a QR code via `QrEncoder`.
   - The server only runs while `QrShareDialog` is open — dismissing it calls `SettingsViewModel.stopQrExport()`.
2. **Receiver** taps "Import from QR code", scans the code. `QrScannerScreen` decodes the raw URL via `ZxingImageAnalyzer` and passes it to `SettingsViewModel.onQrScanned`, which:
   - Parses `host`/`port`/`token` from the URL.
   - Fetches the export JSON over HTTP via `ExportSyncClient`.
   - Parses it via `SettingsExportCodec.parseExportData` and surfaces the standard Add/Replace `ImportConfirmDialog`.

## Edge Cases

- Devices not on the same Wi-Fi/LAN → fetch times out (`AppConstants.SyncConstants.EXPORT_FETCH_TIMEOUT_MS`), error snackbar shown.
- Malformed/foreign QR code (missing `host`/`port`/`token`) → "Invalid QR code" error snackbar, no fetch attempted.
- No size limit on the export — the HTTP body is just the same JSON used by file export.
