# Changelog

Format based on Keep a Changelog. Older internal build notes are in `docs/archive/`.

## [15.8] - 2026-11-23
First public preview. Tested on one Geely X7 2019 head unit (800 x 480, Android 4.x).

### Added
- Three themes: Carbon, Electric, Day
- Analog clock, USB music browse/resume, radio and Android Auto (OpenAuto) shortcuts, app grid
- OBD2 readout over Bluetooth ELM327 or phone bridge, with a labelled demo mode
- Touch stability fixes for the head unit's touch panel
- Release-signed APK built by GitHub Actions, with SHA-256 checksums

### Known issues
- Day theme's Apps button can show slight shading differences
- Layout is fixed to 800 x 480; other screens are untested
- Android Auto needs a separate compatible app, phone and cable
- Not verified on other cars, firmware or Android versions
