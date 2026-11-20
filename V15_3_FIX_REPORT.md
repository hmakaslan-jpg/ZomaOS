# V15.3 Functional Fix Beta

- Fixed live media player: album artwork when embedded, readable ellipsized title/artist, and live seek progress from MediaPlayer.
- Mapped five visible sidebar icons rather than seven invisible hit slots.
- Top Wi-Fi/Bluetooth decorative symbols are no longer touch shortcuts to Settings.
- Music seek and transport touch regions aligned with new media overlay.
- Preserved existing USB resume and dock functionality.

## Known limitations
- Clock hands and version/header text are baked into original background art and may still ghost or show old version labels. Needs clean source artwork.
- Daylight art itself has poor visual contrast; overlay improves player only, not entire artwork.
- No Android SDK/Gradle build or in-car runtime verification was possible in this environment.
- GitHub retains old resources unless repository files are deleted; build workflow cleanup must remain.
