# Beaver Node app feature and API guide

Version 0.1.0 replaces laptop-based Wi-Fi commissioning with a native Android
interface for the existing ESP32 API. No firmware change is required for the
features described here. It supports Android 10 and newer and communicates
locally with the node, without a cloud service or internet requirement.

## Connection

Enable the node AP using its physical INPUT_1 button, then use **Connect** in the
app. Android asks the user to approve joining `Beaver`. On phones where the
automatic flow is unsuitable, open **Wi-Fi settings**, connect manually using
password `12345678`, return to the app and choose **Use Wi-Fi**.

All device requests use the selected Wi-Fi network and fixed address
`http://192.168.4.1`. The app displays the device MAC after verifying a status
response. Verify that MAC before editing settings, particularly when multiple
nodes advertise the same SSID. A valid response identifies the device but is not
cryptographic authentication. Wi-Fi permissions depend on Android version.

## Live screen

- Displays frequency in Hz and raw NTC resistance in ohms for all eight channels.
- Displays channel quality flags, including frequency fault, NTC fault, stale
  sample and no received sample.
- Displays battery percentage, PCB temperature in Celsius and sample age.
- Keeps absent/null auxiliary measurements distinct from valid zero values.
- Polls status every five seconds while the Live screen is in the foreground.
- Supports manual refresh and preserves scrolling during live refresh.

These are the latest RAM measurements, not necessarily the last committed flash
record. The app does not convert NTC resistance to temperature, average data or
filter spikes. It reports the firmware's quality flags.

## Device screen

The user can edit the logging interval, the node's own mesh address and the
gateway target address. The interval accepts 300–604800 seconds; 900 seconds
means 15 minutes. Changing the interval restarts the firmware's logging countdown.

The RTC can be synchronized with the phone's current clock or set using date and
time pickers. Manual times are interpreted in the phone's time zone and converted
to UTC Unix seconds. Ambiguous or nonexistent daylight-saving times are rejected.
The app accepts the firmware's 2024–2099 UTC range and reads status after setting
time. RTC changes do not change the monotonic logging cadence.

## Radio screen

| Setting | App and firmware contract |
|---|---|
| Node ID | Own mesh address; role-dependent range |
| Target ID | Gateway-connected routing node, 0–32767; different from own ID |
| PANID | Mesh network identifier, 0–65534 |
| Role | 0 routing, 1 terminal |
| Channel | Module index 0–79 |
| Air rate | 0 = 62.5K, 1 = 21.825K, 2 = 7K |
| Transmit power | Integer -9 to +22 dBm |
| Route timeout | 1000–65535 milliseconds |
| Link ACK timeout | 1000–65535 milliseconds |
| Gateway application ACK timeout | 5000–300000 milliseconds |
| Radio enable | Enables or disables backlog transmission |

Routing IDs are 0–32767; terminal IDs are 32768–65534. The app checks that the
application ACK timeout is at least route timeout + link ACK timeout + 5000 ms.
All setting fields require integers except the radio-enable Boolean.

Before a settings write, the app reads fresh configuration, merges the edited
fields and validates the resulting combination. It submits only the changed
fields. The confirmation dialog identifies changes and warns when a target or
PANID change will replay retained history. Leaving an editing screen prompts
before discarding the form.

Channel and power ranges are module capabilities, not an India approval list.
The installer must choose approved deployment settings. The app does not supply
a regional preset or expose module encryption-key programming. Settings save
asynchronously with respect to E52 configuration: check Health afterward.

## Logs screen

Download the full retained snapshot or an inclusive sequence range. The app
first reads stream identity and sequence bounds so it can validate the export.
Both old 32-column CSV and new 34-column CSV are supported.

Before a download becomes a saved file, the app checks:

- HTTP status and expected CSV header/column count.
- Firmware `ERROR,...` rows, including overwrite or cancellation errors that can
  accompany an HTTP 200 response.
- Stream identity and strictly increasing sequence IDs within requested bounds.
- Numeric fields, auxiliary values and the requested ending sequence.

Sequence and stream IDs use exact integer/string handling, avoiding floating-point
rounding of uint64 values. Gaps between records are allowed. For a custom range,
its ending sequence must be a retained valid record; a range ending in a hole is
treated as incomplete. Full downloads use the last valid sequence from status.

Completed downloads are kept privately on the phone and remain available offline.
Users can save them to a chosen Android document location, share them with another
app, or delete the phone copy. None of these actions deletes ESP32 logs or advances
the gateway delivery cursor. Uninstalling the app removes its private copies;
explicitly exported files remain in the chosen destination.

The app shows the exact DATA-row count for the selected downloaded file, the
stream, and first/last sequences. It displays the most recent 100 records, with a
detail dialog for all eight channels, quality flags, timestamp and auxiliary data.
A selectable-channel frequency plot uses UTC position and excludes invalid-time
or faulty/stale records. The complete CSV is retained even though the preview is
limited to 100 rows. Resistance, battery and PCB-temperature charts are not yet
implemented.

Keep the app open during downloads. It is not a background download service and
does not automatically resume interrupted downloads. Failed temporary downloads
are not exposed as completed CSV files.

## Health screen

Shows device identity, RTC validity, AP and radio state, received/rejected frames,
UART overflows, missed logs, radio retries, stream identity, retained sequence
bounds, delivery cursor, nominal storage capacity and storage-fault status.

Radio error meanings are explained on screen: configuration failure, storage
read failure, cursor checkpoint failure, and gateway ACK timeout. `radio_ready`
indicates configuration success, not durable delivery; use cursor progress to
check acknowledgements. Sequence bounds are not an exact live stored-record count.

The user can request AP shutdown. The app disconnects afterward, and the physical
node button must be used to enable Wi-Fi again. It cannot turn on an inactive AP
remotely through Wi-Fi.

## API mapping

| Operation | Endpoint |
|---|---|
| Live data and diagnostics | GET `/api/v1/status` |
| Read desired settings | GET `/api/v1/config` |
| Save changed settings | PUT `/api/v1/config` |
| Synchronize or manually set RTC | PUT `/api/v1/time` |
| Download retained logs | GET `/api/v1/logs.csv` with optional `from` and `to` |
| Disable node AP | POST `/api/v1/ap/disable` |

Connection errors and firmware rejections are shown to the user. A timeout after
a settings write has an unknown outcome: read configuration before retrying,
because the device might already have saved it.

## Security and current limits

The app allows unencrypted HTTP only to the node IP. It has no analytics, cloud
account or custom backend. Saved CSV sharing uses a read-only content provider
with temporary URI grants. Broad storage access is not requested. Backup is
disabled; explicitly save/share files that need to outlive the installation.

Access control remains the firmware's shared AP credentials. There is no separate
API login or certificate-based device authentication. AP credential changes,
firmware updates, factory reset, node log deletion, live exact log counts and
remote internet access are not implemented. Firmware-version discovery is also
absent from the current API.

The app is English-only and debug-signed for bench testing. Build, lint and 17 JVM
tests have passed, but physical-phone/emulator runtime and phone-to-ESP32 tests
have not yet been performed. See `VALIDATION.md` and the README acceptance steps.
