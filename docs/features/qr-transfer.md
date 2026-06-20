# QR Share / Transfer

Settings → share or import config between devices over the local network, kicked off by scanning a single QR code **or** typing a 6-character code. Both devices must be on the same Wi-Fi/LAN.

Key files: `:feature:settings:impl/ExportSyncServer.kt`, `:feature:settings:impl/ExportSyncClient.kt`, `:feature:settings:impl/QrScannerScreen.kt`, `:feature:settings:impl/QrShareDialog.kt`, `:feature:settings:impl/QrEncoder.kt`, `:core:common/util/NetworkUtils.kt`, `:core:common/util/NsdCodeManager.kt`, `:core:common/util/RandomCode.kt`

## Mechanism

The QR code does not carry the export data itself — it carries connection info for a transient local HTTP server. This avoids any size limit, multi-QR chunking, or scan-sequence logic. The same connection is also reachable by typing a short code, mirroring Group Sync's leader/follower discovery (`NsdCodeManager`, shared by both features).

1. **Sender** taps "Export via QR code". `SettingsViewModel.prepareQrExport()`:
   - Serializes current settings via `SettingsExportCodec.serializeExportData`.
   - Generates a random 6-character code via `RandomCode.generate()`.
   - Starts `ExportSyncServer` (a `ServerSocket(0)`-bound local HTTP server, OS-assigned port), which serves that JSON at `GET /export?token=CODE`.
   - Registers the code for NSD discovery via `NsdCodeManager.startAdvertising(code, port)`.
   - Builds `locationjoystick://export?host=HOST&port=PORT&token=CODE` (HOST resolved via `NetworkUtils.getLocalIpAddress()`) and renders it as a QR code via `QrEncoder`. `QrShareDialog` displays the code alongside the QR for manual entry.
   - Both the HTTP server and NSD advertising only run while `QrShareDialog` is open — dismissing it calls `SettingsViewModel.stopQrExport()`.
2. **Receiver** has two options, both ending in the same fetch:
   - **Scan**: taps "Import from QR code", scans the code. `QrScannerScreen` decodes the raw URL via `ZxingImageAnalyzer` and passes it to `SettingsViewModel.onQrScanned`, which parses `host`/`port`/`token` from the URL.
   - **Type code**: taps "Import via code", enters the 6-character code. `SettingsViewModel.onExportCodeEntered` resolves it to a host:port via `NsdCodeManager.discoverByCode`.
   - Either path then fetches the export JSON over HTTP via `ExportSyncClient`, parses it via `SettingsExportCodec.parseExportData`, and surfaces the standard Add/Replace `ImportConfirmDialog`.

## Edge Cases

- Devices not on the same Wi-Fi/LAN → fetch (or NSD discovery) times out (`AppConstants.SyncConstants.EXPORT_FETCH_TIMEOUT_MS` / `NSD_DISCOVERY_TIMEOUT_MS`), error snackbar shown.
- Malformed/foreign QR code (missing `host`/`port`/`token`) → "Invalid QR code" error snackbar, no fetch attempted.
- Typed code not found within the NSD discovery window → "No sender found for code" error snackbar.
- No size limit on the export — the HTTP body is just the same JSON used by file export.
