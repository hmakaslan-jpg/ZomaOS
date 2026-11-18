# ZomaOS V12.2 - Premium Neon + Simple Midnight (engineering preview)

- Approved Premium Neon 800x480 background images for Electric, Pearl, Twilight.
- New fourth appearance **Simple Midnight** drawn entirely with Android Canvas (no additional bitmap asset). Select via Settings > Appearance.
- Keeps V12.1 clock-face replacement, dock index fixes, Wi-Fi and Bluetooth Settings shortcuts.
- Adds live elapsed/duration bar for **in-app local/USB MediaPlayer only**, using actual getCurrentPosition/getDuration; drag/release to seek. Factory radio/player cannot expose metadata through this code.
- FM tile opens original com.spd.radio; only remembers the last requested source, not actual radio playback state or station frequency.
- USB resume code preserved.
- Requires GitHub Actions APK build and real head-unit tests; no physical device test performed.
- Existing package ID unchanged; back up working APK and preferences before installing.
