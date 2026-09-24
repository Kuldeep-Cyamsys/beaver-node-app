# Beaver Node for Android

Repository: https://github.com/Kuldeep-Cyamsys/beaver-node-app

[Download the Android test APK](BeaverNode-0.1.0-debug.apk) ·
[Feature and API guide](APP_GUIDE.md) · [Validation status](VALIDATION.md)

This is the Android app repository. ESP32 firmware is maintained separately in
https://github.com/Kuldeep-Cyamsys/esp32_VW_dev.

Native Android commissioning app for the existing ESP32 Beaver HTTP API v1.
Android 10 or newer. No firmware change is required for the supported features.
The project uses Java 17, Android Gradle Plugin 8.13.2, Gradle 8.13 and Android
SDK 36. Target SDK is 36. A future upgrade to target 37 must add the applicable
Android local-network permission flow before release.

## What the app covers

| Screen | Features |
|---|---|
| **Connection** | Connect to the Beaver AP through Android's connection dialog, or manually through Wi-Fi settings. Displays the connected device's MAC address. |
| **Live** | Shows all eight frequencies and NTC resistances, channel fault flags, sample age, battery percentage and PCB temperature. Refreshes every five seconds while open. |
| **Device** | Changes logging interval, own node ID and gateway target ID. Synchronizes the RTC with phone time or accepts a manually selected date/time. |
| **Radio** | Configures node role, PANID, channel, air rate, transmit power, route timeout, link ACK timeout, application ACK timeout and radio enable/disable. |
| **Logs** | Downloads all retained logs or a sequence range, stores completed files on the phone, and supports saving, sharing and deleting phone copies. |
| **Log viewer** | Shows the downloaded record count, recent 100 records, full channel details and a selectable-channel frequency plot. |
| **Health** | Displays RTC validity, UART errors, rejected frames, missed logs, radio retries, storage state and gateway delivery progress. Also allows AP shutdown. |

## Install and connect

1. Install the provided debug APK on your Android phone. Allow installation from
   the browser/file manager used to open it when Android requests it.
2. Press INPUT_1 on the ESP32 to enable its access point; LED_1 should light.
3. Open **Beaver Node**, tap **Connect**, grant the requested Nearby Wi-Fi or
   location permission, and approve Android's Beaver connection dialog.
4. If that device's connection dialog does not work, tap **Wi-Fi settings**, join
   **Beaver** with password **12345678**, stay connected despite the no-internet
   warning, then return and tap **Use Wi-Fi**. Android 10–12 may also require the
   phone's Location setting for the automatic Wi-Fi request.
5. Verify the displayed device MAC before changing configuration. Multiple nodes
   currently advertise the same SSID. Enable one node's AP at a time when possible.

The device address is fixed at `http://192.168.4.1`. Device requests explicitly use
the selected Wi-Fi Network, so cellular internet is not used for these requests.
Cleartext HTTP is allowed only for this host. There is no cloud backend, analytics,
or account login. Existing firmware shares commissioning access among AP clients.

## Screens

- **Live**: eight frequencies, NTC resistances, channel faults, sample age, battery
  and PCB temperature. Polls every five seconds while this screen is foreground.
  Values describe RAM frames, not stored logs. Unavailable auxiliary values stay
  unavailable; zero remains a measurement.
- **Device**: logging interval, own ID, gateway ID; sync RTC with phone UTC or
  choose a local date/time. Local time is converted to UTC seconds. Ambiguous or
  nonexistent daylight-saving times are rejected. Supported UTC years: 2024–2099.
- **Radio**: role, node/target ID, PANID, channel, rate, power, three timeouts and
  enable switch. Only edited fields are sent after a fresh configuration read.
  Default RF settings are placeholders, not an India deployment preset. Verify
  channel and power for the installation before enabling transmission.
- **Logs**: complete retained snapshot or sequence-range download, exact count in
  the downloaded file, private local copies, save via Android document picker,
  sharing, recent 100 rows and per-channel frequency plot. Tap rows for full data.
  Preview is bounded to 100 rows, but CSV files contain all downloaded rows.
- **Health**: RTC, UART, storage and radio diagnostics, persistent delivery cursor,
  status refresh and AP shutdown. After AP shutdown the physical button is needed.

## Export semantics

The app reads the current stream and sequence bounds, then requests explicit
bounds so it can detect premature end of data. It accepts the legacy 32-column
CSV and the new 34-column format with the two auxiliary fields. Blank values
are not converted to zero. It rejects malformed rows, wrong stream, duplicate or
descending sequences, HTTP errors, firmware `ERROR,...` rows, and an export that
ends before the requested last sequence. Sequence holes within an export are
allowed. For a custom range, **the ending sequence must identify a retained valid
record**; a range ending in a hole will be rejected as incomplete. Full downloads
automatically use the last valid sequence reported by status.

Only checked downloads become saved `.csv` files. Failed temporary files are
discarded. Saved downloads survive app restarts but are removed on uninstall;
use **Save CSV to phone** for a copy independent of the app. Downloading does not
change gateway delivery state. No device-log delete endpoint exists or is added.
The exact count shown is the number of DATA rows in that phone file, not a live
ESP32 occupancy count. Plots use UTC position and valid samples only; they do not
interpolate missing or faulty readings.

## Build

Open this folder in Android Studio or use a Java 17 terminal with Android SDK 36
and Build Tools 35.0.0 installed. Put your SDK path in untracked `local.properties`:

```properties
sdk.dir=C\:/path/to/Android/Sdk
```

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.
The debug build is for bench testing. A release intended for distribution needs
your own release signing key, release build and device acceptance testing.
Never commit signing keys or passwords. No Play Store publication is performed.

## Validation and device acceptance

Unit tests cover role/address validation, timeout constraints, numeric types,
uint64 precision, legacy/new CSV, null versus zero, export errors/truncation,
sequence holes, ordering, empty stores and bounded previews. Check `VALIDATION.md`
for commands actually run and their outcome.

Required on a phone and ESP32 before field use:

1. Connect with cellular data enabled and Beaver reporting no internet.
2. Confirm all eight mappings, null/zero auxiliary values and stale handling.
3. Set interval 300, read it back and export after a complete logging interval.
4. Sync RTC and compare phone/device time; manually set a time and verify UTC.
5. Set unique node/target IDs, role and approved RF settings; confirm radio_ready
   and durable delivery_cursor progress independently.
6. Interrupt Wi-Fi during download; verify no partial file appears as complete.
7. Save/share a completed CSV and compare it to the laptop export.
8. Rotate the phone, background/resume the app, deny Wi-Fi permissions, reconnect,
   and disable/re-enable the AP. Saved files should remain available offline.
9. Treat a timed-out settings write as an unknown outcome: read configuration
   again before retrying, because the device might have saved it.

## Implementation references

- Android Wi-Fi peer request API:
  https://developer.android.com/develop/connectivity/wifi/wifi-bootstrap
- Android cleartext configuration:
  https://developer.android.com/privacy-and-security/security-config
- Android local network permissions:
  https://developer.android.com/privacy-and-security/local-network-permission
- Firmware: `hello_world/main/web.c`, `main/settings.c`, `docs/API.md`.

Firmware API version reporting, AP credential changes, exact live record count,
new radio encryption settings, OTA and NTC conversion are outside this app's
current contract. The app does not claim these capabilities.
