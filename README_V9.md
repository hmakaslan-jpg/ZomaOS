# ZomaOS NEXUS V9.0 RC1 (source)

Target: Android 4.1 (API 16), 800x480 Geely stock head unit.

Changes from V8.1:
- Dedicated Daylight cockpit drawn independently from the Night background (not a brightness filter).
- Smaller live analog clock, reduced radius 83 -> 65 px.
- Full-screen dark Settings list, 4-column icon App Center, and folder browsing page.
- Seek gesture commits on finger release to avoid repeated seeks on every movement.
- Oil alert moved to lower-right above footer; current reminder threshold retained (500 km).
- Oil alert action: record oil changed at current manually entered odometer with confirmation, or hide alert while keeping all maintenance data.
- Oil reminder visibility toggle in Settings. No automatic reset at 200 km remaining.
- Keep successful audio-source switching; single MediaPlayer released before new track.
- Version 9.0-rc1.

Known limitations:
- Not yet built/tested on actual Geely hardware. GitHub Actions build and device QA required.
- Factory FM radio remains an external app, pending inspection of its APK and MCU interface.
- Steering-wheel keys may still be controlled by the factory MCU.
- Current app UI is drawn in English. Some legacy dialogs remain system style.
- Daylight artwork uses a dedicated layout with the existing road image as a subdued decorative element; final art direction may need polish.

QA checklist:
1. Night/Daylight switch, restart, verify persistence.
2. Check analog clock and USB FILES button hit target.
3. Open Settings, scroll fast, open Apps grid and folder browser.
4. Play USB track, scrub seek, skip, change tracks, switch to FM and back; check for duplicate sound.
5. With 200 km remaining, tap oil reminder, choose Hide, ensure service readings unchanged.
6. Re-show reminder from Settings, and only confirm Oil changed after actual oil service.
7. Check Home launcher selection and startup after reboot.


## RC1 design revision
- Independent daylight background with a cropped SUV photo; no fixed time, music, weather, or V7 branding in the daytime artwork.
- Night V7.3 branding covered at runtime by NEXUS V9.
- Analog clock reduced to radius 52 and moved up; full square backing hides baked hands.
- App grid recycles cell containers.
- Original audio routing, oil-change recording, and oil reminder visibility retained.
- This source has not been built on the Geely head unit.
