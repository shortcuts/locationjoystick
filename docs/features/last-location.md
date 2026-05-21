# Last Remembered Location

On app restart, restores the last spoofed position. No manual re-entry needed.

## DataStore Keys (`:core:datastore`)

| Key | Type | Purpose |
|-----|------|---------|
| `REMEMBER_LAST_LOCATION` | `Boolean` | Feature toggle |
| `LAST_LATITUDE` | `Double` | Last spoofed latitude |
| `LAST_LONGITUDE` | `Double` | Last spoofed longitude |

## Behaviour

- On service start: if `REMEMBER_LAST_LOCATION` is `true` and valid coordinates exist, seed initial position from `LAST_LATITUDE`/`LAST_LONGITUDE`.
- On each position update: persist current coordinates to DataStore.
