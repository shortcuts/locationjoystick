# QR Share / Transfer

Share or import settings between two devices over the local network. The sender shows a QR code or a short typed code. The receiver scans the code or types it. Both devices must be on the same Wi-Fi or LAN.

Key files: the settings feature's export server, client, scanner and share dialog, and the shared network and discovery helpers in `:core:common`.

## Why a QR code carries a connection, not the data

The QR code holds only the address of a short-lived local server. The export itself is fetched over that connection. This avoids size limits, multi-code chunking and scan-sequence logic. A typed code reaches the same server through the same discovery used by Group Sync ([group-sync.md](group-sync.md)).

## Flow

1. **Sender:** opens the share dialog. The app serialises the current settings, generates a short code, and starts a local server on a free port that serves the export only under that code. The server and the discovery advertisement run only while the dialog is open. Closing the dialog stops both.

The export advertisement keeps the group advertisement's service type, name format and code length, so a typed-code import on an older app still finds it. A TXT role mark tells the Group nearby hint to skip it; older apps ignore the mark.
2. **Receiver:** either scans the QR code, or types the short code. Both give the same host, port and code. The receiver fetches the export over HTTP, parses it, and shows the same add-or-replace confirmation as a file import.

The shown code and QR are only ready once the server and discovery are up. The dialog shows a loading state until then.

## Edge cases

- Devices on different networks: the fetch or discovery times out and the user sees an error.
- A QR code that is not ours, or is missing its parts: rejected before any fetch.
- A typed code that no sender answers for within the discovery window: an error.
- Hotspots often block multicast between clients, so typed-code discovery can fail there. The scan path works, because it uses a direct address. See [group-sync.md](group-sync.md), "Why discovery is secondary".
- The export has no size limit. The body is the same JSON as the file export ([export-import.md](export-import.md)).
