# QR Share / Transfer

Settings → share or import config between devices via QR codes. Export splits `ExportData` into scannable JSON chunks encoded as QR codes. Import scans chunks sequentially, reassembles, validates, and imports.

Key files: `:feature:settings:impl/QrScannerScreen.kt`, `:feature:settings:impl/QrShareDialog.kt`, `:feature:settings:impl/QrEncoder.kt`, `:feature:settings:impl/QrChunker.kt`

## Chunking

`QrChunker` splits serialized JSON into chunks sized for QR capacity (alphanumeric mode, ~4296 chars max per QR). Each chunk is prefixed with a `ChunkEnvelope` containing:
- chunk index
- total count
- checksum

## Scanner

`QrScannerScreen` uses ZXing (`CameraX` + `ImageAnalysis`) to scan QR codes.

- `ZxingImageAnalyzer` decodes frames.
- `ChunkReassembler` collects chunks in order, validates checksum, reassembles JSON.

## Share Dialog

`QrShareDialog` displays chunks as QR images via `QrEncoder` (ZXing `MultiFormatWriter`). User swipes through chunks; each is displayed as a Compose `Image` from a generated `Bitmap`.

## Edge Cases

- Chunk lost during scan → reassembler detects missing index, shows progress indicator.
- Corrupted chunk → checksum mismatch, prompt retry.
- Large exports produce many chunks — show progress indicator throughout.
