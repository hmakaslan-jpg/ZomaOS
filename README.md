# ZomaOS NEXUS V7.3 — Exact Concept Artwork Build

Android 4.1 / API 16, 800x480. This project **uses the exact user-approved concept PNG** as its background artwork. It does not reproduce every rendered element as a functional widget. The analog watch, vehicle and atmospheric lighting are static image elements. The two baked-in clocks and example music panel are covered by real-time time and media widgets; bottom climate strip is covered because no car HVAC integration is available. The wallpaper also contains example weather/USB labels in non-interactive regions: those are visual artwork, not live data.

### Working features
- USB browser with selectable mounted storage paths and folders; plays common audio formats directly from readable file paths via MediaPlayer, including next/previous, pause and play. Format decoding depends on factory Android firmware.
- Factory FM Radio via `com.spd.radio` launch intent.
- Factory Geely Music via `com.spd.media` launch intent.
- Android Auto via `me.ri3d.openauto`.
- OBD existing modes retained; no fabricated telemetry.
- 800x480 full-screen exact reference image, overlay widgets and touch targets.

### Limitations
- Factory Geely Music metadata and steering-wheel media controls remain unintegrated.
- The reference image is 1600x960 and scales down to 800x480. Some decorative text remains baked into artwork.
- No live weather, vehicle HVAC control, live analog hands, or simulated track information.
- Large folders are limited to 2000 audio files for memory use. Direct filesystem access requires readable USB mount; USB path availability varies by firmware.
- Source has not been built/tested on the head unit; run the included GitHub Actions workflow and test USB, radio, Auto, and player on-device.

### GitHub
Upload all files from the root of this ZIP into the repo, including `.github/workflows/build-apk.yml`, then trigger Actions.
