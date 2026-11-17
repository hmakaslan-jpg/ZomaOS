# ZomaOS NEXUS V8 — Functional Beta

For Geely X7 stock head unit, Android 4.1 / API 16, 800×480.

## Changes from V7.3
- Keeps exact approved cinematic concept background artwork, with working overlays; removes duplicated bottom row.
- Live analog clock covers the static clock hands from the concept image. Real hour/minute/second hands.
- ZomaOS Settings (left gear): five accent themes, show/hide live analog clock, legacy center display mode, music selection, USB browsing, App Center, maintenance, OBD, Android system settings, reset defaults.
- App Center lists actual installed launchable applications and launches them.
- Music sidebar opens Entertainment Hub; USB browser plays actual readable files, MediaStore-only error no longer blocks USB access.
- USB and indexed local playback: embedded MP3 artwork if available, interactive seek area (drag along 121–445 x / 273–303 y), next/previous/play/pause.
- Main visual buttons mapped to Music, FM Radio, Android Auto, OBD and Apps.
- Maintenance Center: manually enter current odometer and odometer at last oil service, shows oil service interval of 7000 km as **editable-in-code default only**, not a recommendation. No automatic mileage tracking or false GPS/OBD odometer.
- Best-effort media-key event handling when the head unit delivers standard Android key events to this Activity. Factory steering-wheel controls may remain unsupported.
- Existing Bluetooth ELM327 and phone bridge telemetry code retained; requires real compatible hardware and connection. Demo clearly labelled.

## Honest limitations
- **Source beta, NOT built or tested on device.** Run GitHub Actions to build. No APK included.
- The concept PNG is a static artwork, including some visual baked-in decorations. Themes recolor live elements only, not pixels of background.
- No real radio station metadata or factory player metadata integration; Radio opens `com.spd.radio`.
- No GPS or guaranteed ECU odometer. OBD speed/RPM/temp require a connected and supported adapter.
- Album artwork appears only when embedded in playable files and decodable by Android 4.1.
- Media key handling is best effort; MCU/steering wheel may never dispatch standard keys to the app.
- USB paths and codecs depend on firmware. Avoid interacting with screen while driving.

## Build
Upload the **contents** of this folder to GitHub repository root. The included `.github/workflows/build-apk.yml` builds a debug APK using Gradle 8.7 and JDK 17.
