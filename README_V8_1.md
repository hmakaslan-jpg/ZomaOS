# ZomaOS NEXUS V8.1 – Audio Safety & Appearance Beta

Based on the tested V8 Build Fix source, for Geely X7 Android 4.1 / API 16 / 800×480.

## Implemented in this beta
- Appearance selector: Night (original concept) and a brighter, silver-tinted Daylight treatment of the original artwork. This is NOT a new independently illustrated layout.
- 12/24-hour clock preference.
- Audio-focus request for USB and local playback; pause on focus loss and when leaving ZomaOS; stop/release before opening factory FM or music player; explicit Stop all ZomaOS audio in Settings and a bottom-right stop shortcut.
- Android HOME/DEFAULT categories so the system may offer ZomaOS as a home launcher.
- Editable oil-service interval in Settings; service due/soon banner when manual odometer data is populated.
- Enlarged live analog overlay to cover more of the baked-in clock.

## Not yet solved / limitations
- Factory FM may be driven by MCU and ignore Android audio focus. Steering wheel media buttons still control the factory radio and are NOT guaranteed to control ZomaOS.
- Embedded radio controls, full-screen native Settings/Apps/USB browser, direct Wi-Fi/Bluetooth toggles, exact cleanup of original baked-in clock, and a genuinely separate second layout are not implemented in this beta.
- Do not use steering-wheel track buttons to test ZomaOS while driving.
- This source has not been compiled or tested on a physical device in this environment. Build with the included GitHub Actions workflow; keep V8 APK as fallback.
