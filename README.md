# ZomaOS V5 — Cinematic Cockpit (source)

Based on the user-tested V4 source. Android minSdk 16, 800x480, same applicationId com.zoma.os.

## Changes
- Layered perspective road and pseudo-3D shaded car with lighting, designed to be inexpensive on old hardware. **Not real 3D rendering.**
- Integrated media launcher panel. First tap prompts to select a music player based on installed launcher apps, with an **All apps** option for obscure factory package names. Selection persists.
- Long press MUSIC or media panel to reselect the installed music app.
- Existing OpenAuto button, analog/digital clocks, themes, telemetry demo/OBD client retained.

## Important limitations
- Track metadata, album art, folder navigation and media transport controls are NOT integrated with the factory player yet. The media panel is a launcher, not a live player widget.
- Factory Music may not be exposed as a normal launcher activity. If it does not appear, gather its package/activity info; don't claim this release fixes it.
- OBD hardware has not been tested. Demo is labeled.
- GitHub Actions compile and physical device testing are still required. Keep V4 backup.

## Build
Upload project tree to the existing GitHub repo (keep `.github/workflows/build-apk.yml` intact), commit, open Actions, and download the successful run's APK artifact.
