# ZomaOS V15.2 Lite Premium — static optimization report

- Starting point: V15.1 Clean source.
- Removed unreferenced image resources: 2 (n14_scene_twilight.jpg, zoma_icon.png).
- Converted three opaque 800x480 master backgrounds from PNG to high-quality JPEG (quality 89, 4:4:4):
  - master_carbon: 560,309 -> 148,591 bytes (73.5% smaller)
  - master_pearl: 566,354 -> 131,945 bytes (76.7% smaller)
  - master_electric: 574,169 -> 175,691 bytes (69.4% smaller)
- All Java `R.drawable` references checked against remaining resource names.
- No media/radio/Android Auto control logic intentionally changed.
- WARNING: Background artwork still contains pre-rendered hands and text, so overlapping live overlays remain a known V15.1 issue. This release does not claim to fix that.
- No Android SDK/Gradle installed in this environment; APK build and on-device tests NOT performed.
- JPEG may slightly alter fine texture; inspect on head unit before release.
