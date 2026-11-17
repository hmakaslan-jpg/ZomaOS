# ZomaOS NEXUS V6 — Cinematic Cockpit

Android 4.1 (API 16), 800×480. Built on the working V5 source and package `com.zoma.os`.

## New
- Scenic photographic visual asset from the NEXUS concept (cropped to exclude fake gauges/music data).
- New dashboard layout, genuine live clock and existing OBD status values.
- Media PREV / PLAY-PAUSE / NEXT buttons send standard Android media key events. **Factory Geely player compatibility is not verified.**
- OPEN ORIGINAL PLAYER launches selected Music app. Long press Music to choose app.
- Preserves OpenAuto, views, themes, OBD/demo and Wi-Fi bridge from V5.

## Build
Upload the contents of this folder (not the outer folder) to the existing GitHub repository; retain `.github/workflows/build-apk.yml`. GitHub Actions produces the debug APK. Build and on-device performance **not yet verified**.

## Safety
The cinematic scene is decorative, **not navigation**. Never interpret demo data as live readings. Do not change factory CAN/MCU settings. Test while parked. Keep the V5 APK as backup.
