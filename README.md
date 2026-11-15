# ZomaOS V4 — Smart Cockpit

Android 4.1 / API 16, landscape 800×480, lightweight Canvas UI.

## Features
- Redesigned cockpit with three center modes: road visualization, analog clock, digital clock.
- Seven bottom actions: OBD, OpenAuto, View, Music, Maps, Apps, Theme.
- Preserves V3 Bluetooth Classic ELM327 standard Mode 01 telemetry, demo and Wi-Fi phone bridge receiver.
- Three accent colors, preferences retained on device.
- Unavailable telemetry is shown as `--`; demo explicitly labeled.
- No root, firmware, or ECU write operations.

## GitHub build
Upload `app/`, root Gradle files and `.github/workflows/build-apk.yml` preserving paths. Commit to main, then Actions > Build ZomaOS V4 APK > Artifacts. Existing workflow from V1 can also build with same Gradle configuration.

## Limitations
- Road graphic is 2D illustration, NOT real GPS/navigation or 3D engine.
- Music button launches installed music app; it does not yet show track metadata or remote playback controls.
- Maps button opens installed supported maps app; if absent shows App not found.
- Phone bridge requires a separate phone-side sender; this package only receives telemetry.
- ELM327 Bluetooth compatibility depends on the head unit exposing standard Android Bluetooth RFCOMM.
- This source has not been compiled/tested on the vehicle; validate APK on the parked head unit.
- Avoid selecting this app as default Home launcher until tested.
