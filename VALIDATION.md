# Validation — Beaver Node

## Version 0.2.0 handoff verification

Checked 21 October 2026 before publishing the 0.2.0 code and integration guide.

- Gradle 8.13 `--offline testDebugUnitTest lintDebug assembleDebug` succeeded
  using the existing Java 17 / SDK installation. Unit-test and APK tasks were
  up-to-date; the retained test results show 17 tests, zero failures or errors.
- Android lint completed with zero errors and two warnings.
- The published 0.2.0 APK matches `app/build/outputs/apk/debug/app-debug.apk`
  byte-for-byte. Its SHA256 is recorded in `APK-SHA256.txt`.
- The toolchain reported SDK XML/location warnings and Gradle deprecation
  warnings; these did not prevent the successful build.
- No fresh hardware acceptance test was performed for this publishing task.
- Integration guide: `docs/integration/Beaver_Node_Android_Integration_Guide.pdf`.
  The source manifest records the implementation files inspected for the guide.

## Historical version 0.1.0 validation

Date: 23 September 2026.

## Completed

- `testDebugUnitTest lintDebug assembleDebug` succeeded with Java 17,
  Gradle 8.13, AGP 8.13.2, Android platform 36 and Build Tools 35.0.0.
- 17 JVM tests passed: 7 configuration/identity tests and 10 CSV tests.
- Android lint completed with **0 errors and 4 warnings**. Warnings concern
  newer Gradle/test-JSON versions, the legacy backup XML recommendation (backup
  is disabled), and an untranslated English UI string. The first build's
  platform-constant and Android 12 location-permission errors were corrected.
- APK signature verified successfully using APK Signature Scheme v2.
- APK manifest verified: `com.cyamsys.beaver`, version `0.1.0`, minimum SDK 29
  (Android 10), target SDK 36; launcher is MainActivity.

No ESP32 firmware file was changed for this app. No phone was installed or
flashed, and no node configuration was changed during development.

## Not yet tested

The app has not yet run on a physical phone or Android emulator. Therefore its
actual screen layout, Android Wi-Fi consent flow, no-internet routing, mobile
file picker/sharing, and end-to-end operations against the ESP32 are unverified.
Use the acceptance steps in README.md before relying on it in the field.

This is a debug-signed bench build, not a Play Store release. Keep the app open
while downloading. The app is not a background download service; an interrupted
or process-killed download may need to be started again. Only fully validated
downloads are exposed as saved CSVs.
