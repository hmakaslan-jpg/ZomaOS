# Changelog

Format based on Keep a Changelog. Older internal build notes are in `docs/archive/`.

## [15.9] - 2026-11-27
Same three themes as 15.8 (Electric, Pearl, Carbon). No animated themes.

### Added
- Settings regrouped into clear sections, English by default with an Arabic option
- Settings > Change default launcher, plus a one-time tip when ZomaOS is not the default launcher
- Unit steering/panel keys skip tracks for USB and local music; Radio button opens the factory radio
- Faster home screen: dock icons and audio state are cached instead of re-read every frame
- Apps list with search, sorting and cached icons
- Optional phone GPS for the speed gauge (needs the companion phone app, off by default), weather and prayer-time chips (off unless used)
- Read-only fault codes, engine temperature alert, settings backup and restore
- Bolder, larger date; names under the five dock icons (USB MUSIC, RADIO FM, ANDROID AUTO, APPS, FILE BROWSER); welcome animation available but off by default

### Not included
- Offline map (planned for a later version)

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
