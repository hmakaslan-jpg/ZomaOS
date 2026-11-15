# ZomaOS V3 Premium Cockpit

Android 4.1 / API 16, 800x480 landscape. Updates the V2.1 source while retaining OpenAuto launch and optional OBD telemetry.

## New in V3
- Three selectable color themes (Ice Blue, Amber Night, Emerald) with persistent selection.
- Three persistent center widgets: road, analog clock, digital clock.
- MUSIC shortcut tries installed local/streaming music apps.
- OPENAUTO launches `me.ri3d.openauto`.
- OBD/DEMO source selection retained; simulated readings clearly labeled.
- Safe read-only Mode 01 OBD polling, optional Wi-Fi bridge client (phone server NOT included).

## Build
Upload contents to GitHub repo root preserving `app/` and `.github/workflows/` paths. The existing GitHub Actions workflow compiles debug APK; artifact named ZomaOS-V3-debug-APK.

## Important
This is a source update, not a locally compiled APK. Install/test at rest. Back up your V1 APK. Debug signing may require uninstalling a differently signed build. No ECU writes. Real OBD adapter not yet validated.
